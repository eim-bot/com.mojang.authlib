package com.mojang.authlib.yggdrasil;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.authlib.Environment;
import com.mojang.authlib.HttpAuthenticationService;
import com.mojang.authlib.exceptions.MinecraftClientException;
import com.mojang.authlib.minecraft.TelemetryEvent;
import com.mojang.authlib.minecraft.TelemetryPropertyContainer;
import com.mojang.authlib.minecraft.TelemetrySession;
import com.mojang.authlib.minecraft.client.MinecraftClient;
import com.mojang.authlib.yggdrasil.request.TelemetryEventsRequest;
import com.mojang.authlib.yggdrasil.response.Response;
import java.net.URL;
import java.time.Instant;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class YggdrassilTelemetrySession implements TelemetrySession {
   private static final Logger LOGGER = LogManager.getLogger();
   private static final String SOURCE = "minecraft.java";
   private final MinecraftClient minecraftClient;
   private final URL routeEvents;
   private final Executor ioExecutor;
   private final JsonObject globalProperties = new JsonObject();
   private Consumer<TelemetryPropertyContainer> eventSetupFunction = event -> {
   };

   @VisibleForTesting
   YggdrassilTelemetrySession(MinecraftClient minecraftClient, Environment environment, Executor ioExecutor) {
      this.minecraftClient = minecraftClient;
      this.routeEvents = HttpAuthenticationService.constantURL(environment.getServicesHost() + "/events");
      this.ioExecutor = ioExecutor;
   }

   @Override
   public boolean isEnabled() {
      return true;
   }

   @Override
   public TelemetryEvent createNewEvent(String type) {
      return new YggdrassilTelemetryEvent(this, type);
   }

   @Override
   public TelemetryPropertyContainer globalProperties() {
      return TelemetryPropertyContainer.forJsonObject(this.globalProperties);
   }

   @Override
   public void eventSetupFunction(Consumer<TelemetryPropertyContainer> eventSetupFunction) {
      this.eventSetupFunction = eventSetupFunction;
   }

   void sendEvent(String type, JsonObject data) {
      Instant sendTime = Instant.now();
      this.globalProperties.entrySet().forEach(e -> data.add((String)e.getKey(), (JsonElement)e.getValue()));
      this.eventSetupFunction.accept(TelemetryPropertyContainer.forJsonObject(data));
      TelemetryEventsRequest.Event request = new TelemetryEventsRequest.Event("minecraft.java", type, sendTime, data);
      this.ioExecutor.execute(() -> {
         try {
            TelemetryEventsRequest envelope = new TelemetryEventsRequest(ImmutableList.of(request));
            this.minecraftClient.post(this.routeEvents, envelope, Response.class);
         } catch (MinecraftClientException var3x) {
            LOGGER.debug("Failed to send telemetry event {}", request.name, var3x);
         }

      });
   }
}

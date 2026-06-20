package com.deathswap.config;

import com.deathswap.DeathSwapMod;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Fire-and-forget poster for the Discord webhook configured in
 * {@link DeathSwapConfig}. Posts run off-thread so the server tick never blocks
 * on the network.
 */
public final class DiscordWebhook {

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private DiscordWebhook() {
    }

    /**
     * POST {@code content} to the configured webhook asynchronously.
     *
     * @return a future completing {@code true} on a 2xx response, or {@code false}
     *         if no webhook is configured or the request failed.
     */
    public static CompletableFuture<Boolean> send(String content) {
        String url = DeathSwapConfig.discordWebhookUrl();
        if (url == null || url.isBlank()) {
            return CompletableFuture.completedFuture(false);
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("content", content);

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();

        return CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .handle((response, error) -> {
                    if (error != null) {
                        DeathSwapMod.LOGGER.warn("Report webhook failed", error);
                        return false;
                    }
                    int code = response.statusCode();
                    if (code / 100 != 2) {
                        DeathSwapMod.LOGGER.warn("Report webhook returned {}: {}", code, response.body());
                        return false;
                    }
                    return true;
                });
    }
}

package io.github.yeisonfjrd.aifix;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Chat con un modelo de Ollama, local y gratis. Sin streaming: esperamos la respuesta entera. */
public final class OllamaClient {

    public record Message(String role, String content) {}

    public record Reply(String content, Duration took) {}

    private static final ObjectMapper JSON = new ObjectMapper();

    private final URI url;
    private final String model;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public OllamaClient(String baseUrl, String model) {
        this.url = URI.create(baseUrl.replaceAll("/$", "") + "/api/chat");
        this.model = model;
    }

    public Reply chat(List<Message> messages) throws IOException, InterruptedException {
        String body = JSON.writeValueAsString(Map.of(
                "model", model,
                "messages", messages,
                "stream", false,
                // Temperatura baja: queremos el arreglo más probable, no creatividad.
                "options", Map.of("temperature", 0.2, "num_ctx", 32768)));
        HttpRequest request = HttpRequest.newBuilder(url)
                .timeout(Duration.ofMinutes(15))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        long start = System.nanoTime();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (java.net.ConnectException e) {
            throw new IOException("No hay Ollama en " + url.resolve("/") + ". ¿Está abierto? (ollama serve)", e);
        }
        if (response.statusCode() != 200) {
            throw new IOException("Ollama respondió " + response.statusCode() + ": " + response.body());
        }
        JsonNode json = JSON.readTree(response.body());
        String content = json.path("message").path("content").asText();
        // Los modelos con razonamiento mandan su borrador entre <think>: no es parte de la respuesta.
        content = content.replaceAll("(?s)<think>.*?</think>", "").strip();
        return new Reply(content, Duration.ofNanos(System.nanoTime() - start));
    }
}

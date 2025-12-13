package io.jettra.driver;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class JettraClient {
    private final String baseUrl;
    private final String username;
    private final String password;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    public JettraClient(String host, int port, String username, String password) {
        this.baseUrl = "http://" + host + ":" + port + "/api";
        this.username = username;
        this.password = password;
        this.httpClient = HttpClient.newHttpClient();
        this.mapper = new ObjectMapper();
    }

    private String getAuthHeader() {
        String auth = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
    }

    private <T> T sendRequest(HttpRequest request, TypeReference<T> typeRef) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                if (typeRef != null && response.body() != null && !response.body().isEmpty()) {
                    return mapper.readValue(response.body(), typeRef);
                }
                return null;
            } else {
                throw new DriverException("Request failed: " + response.statusCode() + " - " + response.body());
            }
        } catch (Exception e) {
            throw new DriverException("Communication error", e);
        }
    }

    // Database Operations
    public void createDatabase(String name) {
        try {
            String body = mapper.writeValueAsString(Collections.singletonMap("name", name));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/dbs"))
                    .header("Authorization", getAuthHeader())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            sendRequest(request, null);
        } catch (Exception e) {
            throw new DriverException("Failed to create database", e);
        }
    }

    public void deleteDatabase(String name) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/dbs?name=" + name))
                .header("Authorization", getAuthHeader())
                .DELETE()
                .build();
        sendRequest(request, null);
    }
    
    public List<String> listDatabases() {
         HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/dbs"))
                .header("Authorization", getAuthHeader())
                .GET()
                .build();
         return sendRequest(request, new TypeReference<List<String>>() {});
    }

    // Collection Operations
    public void createCollection(String db, String col) {
        try {
            Map<String, String> payload = Map.of("database", db, "collection", col);
            String body = mapper.writeValueAsString(payload);
             HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/cols"))
                    .header("Authorization", getAuthHeader())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            sendRequest(request, null);
        } catch (Exception e) {
             throw new DriverException("Failed to create collection", e);
        }
    }
    
    // Document Operations
    public String saveDocument(String db, String col, Map<String, Object> document) {
        try {
            String body = mapper.writeValueAsString(document);
             HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/doc?db=" + db + "&col=" + col))
                    .header("Authorization", getAuthHeader())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            Map<String, String> res = sendRequest(request, new TypeReference<Map<String, String>>() {});
            return res.get("id");
        } catch (Exception e) {
             throw new DriverException("Failed to save document", e);
        }
    }
    
    public Map<String, Object> getDocument(String db, String col, String id) {
          HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/doc?db=" + db + "&col=" + col + "&id=" + id))
                    .header("Authorization", getAuthHeader())
                    .GET()
                    .build();
          return sendRequest(request, new TypeReference<Map<String, Object>>() {});
    }
    
    public List<Map<String, Object>> query(String db, String col, int limit, int offset) {
         HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/query?db=" + db + "&col=" + col + "&limit=" + limit + "&offset=" + offset))
                    .header("Authorization", getAuthHeader())
                    .GET()
                    .build();
          return sendRequest(request, new TypeReference<List<Map<String, Object>>>() {});
    }
}

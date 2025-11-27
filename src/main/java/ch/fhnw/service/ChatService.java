package ch.fhnw.service;

import ch.fhnw.model.LoginData;
import ch.fhnw.model.Message;
import ch.fhnw.model.TokenWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatService {

    private String baseUrl = "http://javaprojects.ch:50001";
    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private String authToken;

    public ChatService() {
        this.httpClient = HttpClient.newHttpClient();
        this.mapper = new ObjectMapper();
    }

    public void setBaseUrl(String url) {
        if (url.endsWith("/")) {
            this.baseUrl = url.substring(0, url.length() - 1);
        } else {
            this.baseUrl = url;
        }
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public boolean ping() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/ping"))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            System.err.println("Ping fehlgeschlagen: " + e.getMessage());
            return false;
        }
    }

    public String register(String user, String password) throws Exception {
        String url = baseUrl + "/user/register";
        LoginData data = new LoginData(user, password);

        String requestBody = mapper.writeValueAsString(data);

        return sendPostRequest(url, requestBody);
    }

    public boolean login(String user, String password) throws Exception {
        String url = baseUrl + "/user/login";
        LoginData data = new LoginData(user, password);

        String requestBody = mapper.writeValueAsString(data);

        String responseJson = sendPostRequest(url, requestBody);

        TokenWrapper tokenWrapper = mapper.readValue(responseJson, TokenWrapper.class);
        this.authToken = tokenWrapper.getToken();

        return this.authToken != null;
    }

    private String sendPostRequest(String url, String jsonBody) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("Server Error " + response.statusCode() + ": " + response.body());
        }

        return response.body();
    }

    public String getAuthToken() {
        return authToken;
    }

    public boolean sendMessage(String recipient, String messageText) throws Exception {
        if (authToken == null) {
            throw new IllegalStateException("Kein Token vorhanden, bitte einloggen.");
        }
        String url = baseUrl + "/chat/send";
        Message msg = new Message(authToken, recipient, messageText);

        String jsonBody = mapper.writeValueAsString(msg);
        String response = sendPostRequest(url, jsonBody);

        return response.contains("true");
    }

    public List<Message> pollMessages() throws Exception {
        if (authToken == null) {
            return List.of();
        }
        String url = baseUrl + "/chat/poll";

        Map<String, String> jsonMap = new HashMap<>();
        jsonMap.put("token", authToken);
        String jsonBody = mapper.writeValueAsString(jsonMap);

        String response = sendPostRequest(url, jsonBody);

        JsonNode rootNode = mapper.readTree(response);

        if (rootNode.has("messages")) {
            JsonNode messagesNode = rootNode.get("messages");
            return mapper.readerFor(new TypeReference<List<Message>>(){})
                    .readValue(messagesNode);
        } else if (rootNode.isArray()) {
            return mapper.readValue(response, new TypeReference<List<Message>>(){});
        }

        return List.of();
    }

    public boolean logout() {
        try {
            if (authToken == null) {
                return true;
            }
            String url = baseUrl + "/user/logout";
            Map<String, String> jsonMap = new HashMap<>();
            jsonMap.put("token", authToken);
            String jsonBody = mapper.writeValueAsString(jsonMap);

            sendPostRequest(url, jsonBody);
            this.authToken = null;
            return true;
        } catch (Exception e) {
            System.err.println("Logout fehlgeschlagen: " + e.getMessage());
            return false;
        }
    }

    public boolean isUserOnline(String usernameToCheck) {
        try {
            if (authToken == null) {
                return false;
            }
            String url = baseUrl + "/user/online";

            Map<String, String> jsonMap = new HashMap<>();
            jsonMap.put("token", authToken);
            jsonMap.put("username", usernameToCheck);

            String jsonBody = mapper.writeValueAsString(jsonMap);

            String response = sendPostRequest(url, jsonBody);

            return response.contains("true");
        } catch (Exception e) {
            System.err.println("Online-Check fehlgeschlagen: " + e.getMessage());
            return false;
        }
    }

    public List<String> fetchAllUsers() {
        try {
            String url = baseUrl + "/users";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                System.err.println("Alle User laden fehlgeschlagen: Status " + response.statusCode());
                return List.of();
            }
            String body = response.body();
            JsonNode node = mapper.readTree(body);
            if (node.has("users") && node.get("users").isArray()) {
                List<String> list = mapper.readValue(node.get("users").traverse(), new TypeReference<List<String>>() {});
                return list.stream().distinct().toList();
            }
            if (node.isArray()) {
                List<String> list = mapper.readValue(body, new TypeReference<List<String>>() {});
                return list.stream().distinct().toList();
            }
            System.err.println("Alle User laden: Unerwartetes Format");
            return List.of();
        } catch (Exception e) {
            System.err.println("Alle User laden fehlgeschlagen: " + e.getMessage());
            return List.of();
        }
    }

    public List<String> fetchOnlineUsers() {
        try {
            if (authToken == null) {
                System.err.println("Online-Liste abgebrochen: kein Token.");
                return List.of();
            }
            // Primär: POST /user/online mit Token (liefert ein Feld "online": [..])
            String url = baseUrl + "/user/online";
            Map<String, String> body = new HashMap<>();
            body.put("token", authToken);
            String jsonBody = mapper.writeValueAsString(body);

            String responseBody = sendPostRequest(url, jsonBody);
            JsonNode node = mapper.readTree(responseBody);
            if (node.has("online") && node.get("online").isArray()) {
                List<String> list = mapper.readValue(node.get("online").traverse(), new TypeReference<List<String>>() {});
                return list.stream().distinct().toList();
            }
            if (node.isArray()) {
                List<String> list = mapper.readValue(responseBody, new TypeReference<List<String>>() {});
                return list.stream().distinct().toList();
            }
            return fetchOnlineUsersFallback();
        } catch (Exception e) {
            System.err.println("Online-Liste konnte nicht geladen werden: " + e.getMessage());
            return fetchOnlineUsersFallback();
        }
    }

    private List<String> fetchOnlineUsersFallback() {
        try {
            String url = baseUrl + "/users/online";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                System.err.println("Fallback Online-Liste Fehler: " + response.statusCode());
                return List.of();
            }
            String body = response.body();
            JsonNode node = mapper.readTree(body);
            if (node.has("online") && node.get("online").isArray()) {
                List<String> list = mapper.readValue(node.get("online").traverse(), new TypeReference<List<String>>() {});
                return list.stream().distinct().toList();
            }
            if (node.isArray()) {
                List<String> list = mapper.readValue(body, new TypeReference<List<String>>() {});
                return list.stream().distinct().toList();
            }
            return List.of();
        } catch (Exception ex) {
            System.err.println("Fallback Online-Liste konnte nicht geladen werden: " + ex.getMessage());
            return List.of();
        }
    }

    public void clearToken() {
        this.authToken = null;
    }
}

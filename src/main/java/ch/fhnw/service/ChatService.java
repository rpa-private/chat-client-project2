package ch.fhnw.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import ch.fhnw.model.LoginData;
import ch.fhnw.model.TokenWrapper;
import ch.fhnw.model.Message;

import java.util.List;
import java.util.HashMap;
import java.util.Map;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class ChatService {

    // Standard URL
    private String baseUrl = "http://javaprojects.ch:50001";

    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    // Hier speichern wir den Token nach erfolgreichem Login
    private String authToken = null;

    public ChatService() {
        this.httpClient = HttpClient.newHttpClient();
        this.mapper = new ObjectMapper();
    }

    // Damit man die URL ändern kann
    public void setBaseUrl(String url) {
        // Entfernt Slash am Ende, falls vorhanden, um Fehler zu vermeiden
        if (url.endsWith("/")) {
            this.baseUrl = url.substring(0, url.length() - 1);
        } else {
            this.baseUrl = url;
        }
    }

    // Testet die Verbindung
    public boolean ping() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/ping"))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200; // Erwartet {"ping":true}
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // --- REGISTRIEREN ---

    public String register(String user, String password) throws Exception {
        String url = baseUrl + "/user/register";
        LoginData data = new LoginData(user, password);

        // Objekt zu JSON String wandeln
        String requestBody = mapper.writeValueAsString(data);

        String response = sendPostRequest(url, requestBody);
        return response; // Gibt den Username zurück oder wirft Fehler
    }

    // --- LOGIN ---
    public boolean login(String user, String password) throws Exception {
        String url = baseUrl + "/user/login";
        LoginData data = new LoginData(user, password);

        String requestBody = mapper.writeValueAsString(data);

        // Anfrage senden
        String responseJson = sendPostRequest(url, requestBody);

        // Antwort (Token) parsen
        TokenWrapper tokenWrapper = mapper.readValue(responseJson, TokenWrapper.class);
        this.authToken = tokenWrapper.getToken();

        return this.authToken != null;
    }

    // Hilfsmethode für alle POST Requests
    private String sendPostRequest(String url, String jsonBody) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            // Wenn der Server Fehler 418 oder ähnliches meldet
            throw new IOException("Server Error " + response.statusCode() + ": " + response.body());
        }

        return response.body();
    }

    // Getter für den Token
    public String getAuthToken() {
        return authToken;
    }

    // --- NACHRICHT SENDEN ---
    public boolean sendMessage(String recipient, String messageText) throws Exception {
        String url = baseUrl + "/chat/send";
        // Message Objekt erstellen
        Message msg = new Message(authToken, recipient, messageText);

        String jsonBody = mapper.writeValueAsString(msg);
        String response = sendPostRequest(url, jsonBody);

        // Server antwortet
        return response.contains("true");
    }

    // --- NACHRICHTEN ABHOLEN ---
    public List<Message> pollMessages() throws Exception {
        String url = baseUrl + "/chat/poll";

        // Nur den Token senden
        Map<String, String> jsonMap = new HashMap<>();
        jsonMap.put("token", authToken);
        String jsonBody = mapper.writeValueAsString(jsonMap);

        String response = sendPostRequest(url, jsonBody);

        // DEBUG: Damit wir sehen, was wirklich zurückkommt (falls es immer noch nicht geht)
        // System.out.println("Server Antwort Poll: " + response);

        // JSON Baum einlesen
        JsonNode rootNode = mapper.readTree(response);

        // Fall 1: Der Server antwortet mit {"messages": [ ... ]} (Wie im PDF Beispiel)
        if (rootNode.has("messages")) {
            JsonNode messagesNode = rootNode.get("messages");
            // Den Inhalt des "messages"-Feldes in eine Liste umwandeln
            return mapper.readerFor(new TypeReference<List<Message>>(){})
                    .readValue(messagesNode);
        }
        // Fall 2: Der Server antwortet direkt mit [ ... ] (Falls er sich anders verhält)
        else if (rootNode.isArray()) {
            return mapper.readValue(response, new TypeReference<List<Message>>(){});
        }

        // Fall 3: Leere Antwort oder Fehler -> Leere Liste zurückgeben
        return List.of();
    }

    // --- LOGOUT ---
    public boolean logout() {
        try {
            String url = baseUrl + "/user/logout";
            Map<String, String> jsonMap = new HashMap<>();
            jsonMap.put("token", authToken);
            String jsonBody = mapper.writeValueAsString(jsonMap);

            sendPostRequest(url, jsonBody);
            this.authToken = null; // Token löschen
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // --- CHECK USER ONLINE ---
    public boolean isUserOnline(String usernameToCheck) {
        try {
            String url = baseUrl + "/user/online";

            // Map für JSON erstellen
            Map<String, String> jsonMap = new HashMap<>();
            jsonMap.put("token", authToken);
            jsonMap.put("username", usernameToCheck);

            String jsonBody = mapper.writeValueAsString(jsonMap);

            // Anfrage senden
            String response = sendPostRequest(url, jsonBody);

            // Server antwortet z.B. mit { "online": true }
            return response.contains("true");
        } catch (Exception e) {
            // Im Fehlerfall gehen wir davon aus, dass er nicht online ist
            return false;
        }
    }

}

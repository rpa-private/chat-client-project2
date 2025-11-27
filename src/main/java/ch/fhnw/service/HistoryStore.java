package ch.fhnw.service;

import ch.fhnw.model.ChatEntry;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class HistoryStore {

    private final Path historyFile;
    private final ObjectMapper mapper;

    public HistoryStore() {
        this(Path.of(System.getProperty("user.home"), ".chat-client-history.json"));
    }

    public HistoryStore(Path historyFile) {
        this.historyFile = historyFile;
        this.mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    }

    private Map<String, Map<String, List<ChatEntry>>> readAll() {
        try {
            if (!Files.exists(historyFile)) {
                return new HashMap<>();
            }
            byte[] data = Files.readAllBytes(historyFile);
            return mapper.readValue(data, new TypeReference<>() {});
        } catch (Exception e) {
            System.err.println("Konnte Chat-History nicht lesen: " + e.getMessage());
            return new HashMap<>();
        }
    }

    private void writeAll(Map<String, Map<String, List<ChatEntry>>> data) {
        try {
            if (historyFile.getParent() != null) {
                Files.createDirectories(historyFile.getParent());
            }
            mapper.writeValue(historyFile.toFile(), data);
        } catch (Exception e) {
            System.err.println("Konnte Chat-History nicht speichern: " + e.getMessage());
        }
    }

    public synchronized void append(String owner, String contact, boolean outgoing, String text) {
        Map<String, Map<String, List<ChatEntry>>> data = readAll();
        Map<String, List<ChatEntry>> userHistory = data.computeIfAbsent(owner, key -> new HashMap<>());
        List<ChatEntry> conversation = userHistory.computeIfAbsent(contact, key -> new ArrayList<>());
        conversation.add(new ChatEntry(contact, outgoing, text, System.currentTimeMillis()));
        writeAll(data);
    }

    public synchronized List<ChatEntry> loadConversation(String owner, String contact) {
        Map<String, Map<String, List<ChatEntry>>> data = readAll();
        return data.getOrDefault(owner, new HashMap<>())
                .getOrDefault(contact, List.of());
    }

    public synchronized List<String> loadContacts(String owner) {
        Map<String, Map<String, List<ChatEntry>>> data = readAll();
        if (!data.containsKey(owner)) {
            return List.of();
        }
        return data.get(owner).keySet().stream().sorted().collect(Collectors.toList());
    }
}

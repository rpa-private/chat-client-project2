package ch.fhnw.chatclient;

import ch.fhnw.model.ChatEntry;
import ch.fhnw.model.Message;
import ch.fhnw.service.ChatService;
import ch.fhnw.service.HistoryStore;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ChatClientApp extends Application {

    private static final String DEFAULT_URL = "http://javaprojects.ch:50001";

    private final ChatService chatService = new ChatService();
    private final HistoryStore historyStore = new HistoryStore();
    private final ExecutorService worker = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        return t;
    });

    private ScheduledExecutorService scheduler;
    private volatile boolean running;

    private Stage primaryStage;
    private Label statusLabel;
    private TextField urlField;
    private TextField usernameField;
    private PasswordField passwordField;

    private ListView<String> contactListView;
    private TextField searchField;
    private ObservableList<String> contacts;
    private FilteredList<String> filteredContacts;
    private ObservableList<String> onlineContacts;
    private Label onlineCountLabel;

    private ListView<ChatEntry> chatListView;
    private ObservableList<ChatEntry> conversationItems;
    private TextField messageField;
    private Label contactStatus;
    private Label headerLabel;

    private String currentUser;
    private String activeContact;
    private boolean shutdownHookRegistered = false;

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        primaryStage.setTitle("Chat Client");
        primaryStage.setOnCloseRequest(event -> {
            shutdownApp();
            Platform.exit();
        });
        registerShutdownHook();

        showLoginScene();
        primaryStage.show();
    }

    @Override
    public void stop() {
        shutdownApp();
    }

    private void showLoginScene() {
        Label title = new Label("Chat Client");
        title.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #075e54;");

        urlField = new TextField(DEFAULT_URL);
        urlField.setPromptText("Server URL");

        usernameField = new TextField();
        usernameField.setPromptText("Username (min. 3 Zeichen)");

        passwordField = new PasswordField();
        passwordField.setPromptText("Passwort");

        Button pingButton = new Button("Ping");
        Button registerButton = new Button("Registrieren");
        Button loginButton = new Button("Login");

        statusLabel = new Label("Bereit");
        statusLabel.setStyle("-fx-text-fill: #555;");

        pingButton.setOnAction(e -> runAsync(this::handlePing));
        registerButton.setOnAction(e -> runAsync(this::handleRegister));
        loginButton.setOnAction(e -> runAsync(this::handleLogin));

        VBox form = new VBox(12,
                title,
                new Label("Server"),
                urlField,
                new Label("Benutzername"),
                usernameField,
                new Label("Passwort"),
                passwordField,
                new HBox(10, pingButton, registerButton, loginButton),
                statusLabel
        );
        form.setPadding(new Insets(24));
        form.setAlignment(Pos.CENTER);
        form.setStyle("-fx-background-color: white; -fx-border-radius: 12; -fx-background-radius: 12; -fx-effect: dropshadow(two-pass-box, rgba(0,0,0,0.1), 12, 0, 0, 4);");

        BorderPane root = new BorderPane();
        root.setCenter(form);
        root.setPadding(new Insets(30));
        root.setStyle("-fx-background-color: linear-gradient(to bottom right, #d7f0e8, #f7f7f7);");

        Scene scene = new Scene(root, 420, 520);
        primaryStage.setScene(scene);
    }

    private void handlePing() {
        chatService.setBaseUrl(urlField.getText().trim());
        boolean ok = chatService.ping();
        updateStatus(ok ? "Server erreichbar" : "Server nicht erreichbar", ok);
    }

    private void handlePingWithToken() {
        boolean ok = chatService.pingWithToken();
        updateStatus(ok ? "Token gültig" : "Token ungültig oder abgelaufen", ok);
    }

    private void handleRegister() {
        chatService.setBaseUrl(urlField.getText().trim());
        try {
            String result = chatService.register(usernameField.getText().trim(), passwordField.getText().trim());
            updateStatus("Registriert: " + result, true);
        } catch (Exception e) {
            updateStatus("Registrierung fehlgeschlagen: " + e.getMessage(), false);
        }
    }

    private void handleLogin() {
        chatService.setBaseUrl(urlField.getText().trim());
        try {
            boolean success = chatService.login(usernameField.getText().trim(), passwordField.getText().trim());
            if (success) {
                boolean tokenOk = chatService.pingWithToken();
                if (!tokenOk) {
                    updateStatus("Login fehlgeschlagen: Token ungültig", false);
                    chatService.clearToken();
                    return;
                }
                currentUser = usernameField.getText().trim();
                Platform.runLater(this::showChatScene);
            } else {
                updateStatus("Login fehlgeschlagen", false);
            }
        } catch (Exception e) {
            updateStatus("Login fehlgeschlagen: " + e.getMessage(), false);
        }
    }

    private void showChatScene() {
        contacts = FXCollections.observableArrayList();
        filteredContacts = new FilteredList<>(contacts, s -> true);
        onlineContacts = FXCollections.observableArrayList();
        conversationItems = FXCollections.observableArrayList();

        contactListView = new ListView<>(filteredContacts);
        contactListView.setPrefWidth(240);
        contactListView.setStyle("-fx-background-color: transparent;");
        contactListView.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
            if (sel != null) {
                selectContact(sel);
            }
        });
        contactListView.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    boolean isOnline = onlineContacts.contains(item);
                    if (isOnline) {
                        setStyle("-fx-font-weight: bold; -fx-text-fill: #000000;");
                    } else {
                        setStyle("-fx-text-fill: #888888;");
                    }
                }
            }
        });

        searchField = new TextField();
        searchField.setPromptText("Suche nach Kontakten");
        searchField.textProperty().addListener((obs, old, text) -> {
            String term = text.toLowerCase();
            filteredContacts.setPredicate(name -> name.toLowerCase().contains(term));
            if (!term.isBlank()) {
                runAsync(this::refreshUsersAndOnline);
            }
        });

        Button refreshButton = new Button("Online aktualisieren");
        refreshButton.setOnAction(e -> runAsync(this::refreshUsersAndOnline));

        Button pingTokenButton = new Button("Ping Token");
        pingTokenButton.setOnAction(e -> runAsync(this::handlePingWithToken));

        statusLabel = new Label("Verbunden mit " + chatService.getBaseUrl());
        statusLabel.setStyle("-fx-text-fill: #555;");

        Label userBadge = new Label(currentUser);
        userBadge.setStyle("-fx-font-weight: bold; -fx-text-fill: #075e54;");

        Button logoutButton = new Button("Logout");
        logoutButton.setOnAction(e -> runAsync(this::logoutAndBack));

        HBox userHeader = new HBox(10, userBadge, logoutButton);
        userHeader.setAlignment(Pos.CENTER_LEFT);

        onlineCountLabel = new Label("Online: 0");
        onlineCountLabel.setStyle("-fx-text-fill: #075e54;");

        VBox left = new VBox(12,
                userHeader,
                searchField,
                onlineCountLabel,
                contactListView,
                new Separator(),
                new HBox(10, refreshButton, pingTokenButton),
                statusLabel
        );
        left.setPadding(new Insets(14));
        left.setPrefWidth(260);
        left.setStyle("-fx-background-color: #ededed;");

        headerLabel = new Label("Keine Unterhaltung gewählt");
        headerLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        contactStatus = new Label("offline");
        contactStatus.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");

        HBox header = new HBox(10, headerLabel, contactStatus);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(10));
        header.setStyle("-fx-background-color: #075e54; -fx-text-fill: white;");
        headerLabel.setStyle("-fx-text-fill: white; -fx-font-size: 16px; -fx-font-weight: bold;");
        contactStatus.setStyle("-fx-text-fill: white; -fx-font-size: 12px; -fx-font-weight: bold;");

        chatListView = new ListView<>(conversationItems);
        chatListView.setFocusTraversable(false);
        chatListView.setStyle("-fx-background-color: #e5ddd5;");
        chatListView.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(ChatEntry item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    Label bubble = new Label(item.getMessage());
                    bubble.setWrapText(true);
                    bubble.setMaxWidth(320);
                    bubble.setPadding(new Insets(8, 12, 8, 12));
                    String bubbleColor = item.isOutgoing() ? "#dcf8c6" : "#ffffff";
                    bubble.setStyle("-fx-background-color: " + bubbleColor + "; -fx-background-radius: 12; -fx-border-radius: 12; -fx-border-color: #dcdcdc;");
                    HBox box = new HBox(bubble);
                    box.setPadding(new Insets(6));
                    box.setAlignment(item.isOutgoing() ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
                    setGraphic(box);
                    setText(null);
                }
            }
        });

        messageField = new TextField();
        messageField.setPromptText("Nachricht eingeben...");
        messageField.setOnAction(e -> sendCurrentMessage());

        Button sendButton = new Button("Senden");
        sendButton.setOnAction(e -> sendCurrentMessage());

        HBox inputBar = new HBox(10, messageField, sendButton);
        HBox.setHgrow(messageField, Priority.ALWAYS);
        inputBar.setPadding(new Insets(10));
        inputBar.setStyle("-fx-background-color: #f0f0f0;");

        VBox right = new VBox(header, chatListView, inputBar);
        VBox.setVgrow(chatListView, Priority.ALWAYS);

        BorderPane root = new BorderPane();
        root.setLeft(left);
        root.setCenter(right);
        root.setStyle("-fx-background-color: #d7f0e8;");

        Scene chatScene = new Scene(root, 900, 600);
        primaryStage.setScene(chatScene);

        loadContactsFromHistory();
        runAsync(this::refreshUsersAndOnline);
        startSchedulers();
    }

    private void loadContactsFromHistory() {
        List<String> known = historyStore.loadContacts(currentUser);
        Platform.runLater(() -> contacts.setAll(known));
    }

    private void selectContact(String contact) {
        activeContact = contact;
        headerLabel.setText(contact);
        updateContactStatus(false);
        loadConversation(contact);
        runAsync(this::refreshOnlineState);
    }

    private void loadConversation(String contact) {
        List<ChatEntry> history = historyStore.loadConversation(currentUser, contact);
        Platform.runLater(() -> {
            conversationItems.setAll(history);
            chatListView.scrollTo(conversationItems.size() - 1);
        });
    }

    private void sendCurrentMessage() {
        String text = messageField.getText().trim();
        if (text.isEmpty() || activeContact == null) {
            return;
        }
        messageField.clear();
        runAsync(() -> {
            try {
                boolean sent = chatService.sendMessage(activeContact, text);
                if (sent) {
                    historyStore.append(currentUser, activeContact, true, text);
                    Platform.runLater(() -> {
                        conversationItems.add(new ChatEntry(activeContact, true, text, System.currentTimeMillis()));
                        chatListView.scrollTo(conversationItems.size() - 1);
                    });
                } else {
                    updateStatus("User offline", false);
                }
            } catch (Exception e) {
                updateStatus("Senden fehlgeschlagen: " + e.getMessage(), false);
            }
        });
    }

    private void pollMessages() {
        if (!running) {
            return;
        }
        try {
            List<Message> messages = chatService.pollMessages();
            if (messages.isEmpty()) {
                return;
            }
            for (Message msg : messages) {
                historyStore.append(currentUser, msg.getUsername(), false, msg.getMessage());
            }
            Platform.runLater(() -> {
                Set<String> newContacts = new HashSet<>();
                for (Message msg : messages) {
                    newContacts.add(msg.getUsername());
                    if (msg.getUsername().equals(activeContact)) {
                        conversationItems.add(new ChatEntry(msg.getUsername(), false, msg.getMessage(), System.currentTimeMillis()));
                    }
                }
                if (!newContacts.isEmpty()) {
                    Set<String> merged = new HashSet<>(contacts);
                    merged.addAll(newContacts);
                    contacts.setAll(merged);
                }
                chatListView.scrollTo(conversationItems.size() - 1);
            });
        } catch (Exception e) {
            System.err.println("Polling Fehler: " + e.getMessage());
        }
    }

    private void refreshUsersAndOnline() {
        List<String> allUsers = chatService.fetchAllUsers();
        List<String> online = chatService.fetchOnlineUsers();
        Set<String> merged = new HashSet<>(allUsers);
        merged.addAll(historyStore.loadContacts(currentUser));
        if (activeContact != null) {
            merged.add(activeContact);
        }
        List<String> sorted = new ArrayList<>(merged);
        Collections.sort(sorted, String::compareToIgnoreCase);

        List<String> uniqueOnline = online.stream().distinct().toList();
        Platform.runLater(() -> {
            contacts.setAll(sorted);
            onlineContacts.setAll(uniqueOnline);
            if (onlineCountLabel != null) {
                onlineCountLabel.setText("Online: " + uniqueOnline.size());
            }
            contactListView.refresh();
        });
    }

    private void refreshOnlineState() {
        if (activeContact == null) {
            return;
        }
        boolean online = chatService.isUserOnline(activeContact);
        Platform.runLater(() -> updateContactStatus(online));
    }

    private void updateContactStatus(boolean online) {
        contactStatus.setText(online ? "online" : "offline");
        contactStatus.setStyle(online
                ? "-fx-text-fill: #c8facc; -fx-font-weight: bold;"
                : "-fx-text-fill: #ffd6d6; -fx-font-weight: bold;");
    }

    private void updateStatus(String text, boolean success) {
        Platform.runLater(() -> {
            statusLabel.setText(text);
            statusLabel.setStyle(success ? "-fx-text-fill: green;" : "-fx-text-fill: red;");
        });
    }

    private void logoutAndBack() {
        stopSchedulers();
        chatService.logout();
        chatService.clearToken();
        currentUser = null;
        activeContact = null;
        Platform.runLater(this::showLoginScene);
    }

    private void startSchedulers() {
        stopSchedulers();
        running = true;
        scheduler = Executors.newScheduledThreadPool(3);
        scheduler.scheduleAtFixedRate(this::pollMessages, 1, 1, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::refreshOnlineState, 2, 4, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::refreshUsersAndOnline, 3, 10, TimeUnit.SECONDS);
    }

    private void stopSchedulers() {
        running = false;
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    private void shutdownApp() {
        stopSchedulers();
        worker.shutdownNow();
        chatService.logout();
    }

    private void runAsync(Runnable task) {
        worker.submit(task);
    }

    private void registerShutdownHook() {
        if (shutdownHookRegistered) {
            return;
        }
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            chatService.logout();
            chatService.clearToken();
        }));
        shutdownHookRegistered = true;
    }

    public static void main(String[] args) {
        launch(args);
    }
}

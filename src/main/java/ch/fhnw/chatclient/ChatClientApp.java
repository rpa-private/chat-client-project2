package ch.fhnw.chatclient;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import ch.fhnw.service.ChatService;

public class ChatClientApp extends Application {

    private ChatService chatService; // Unsere Verbindung zum Server
    private Label statusLabel;       // Zeigt Nachrichten an den User

    @Override
    public void start(Stage primaryStage) {
        // Service initialisieren
        chatService = new ChatService();

        primaryStage.setTitle("Chat Client - Login");

        // --- Layout ---
        VBox root = new VBox(10); // 10px Abstand zwischen Elementen
        root.setPadding(new Insets(20));
        root.setAlignment(Pos.CENTER);

        // --- UI Elemente ---

        // 1. Server URL
        Label urlLabel = new Label("Server URL:");
        TextField urlField = new TextField("http://javaprojects.ch:50001");
        Button pingButton = new Button("Test Connection (Ping)");

        // 2. Login Daten
        TextField userField = new TextField();
        userField.setPromptText("Username (min. 3 Zeichen)");

        PasswordField passField = new PasswordField();
        passField.setPromptText("Password (min. 3 Zeichen)");

        // 3. Buttons
        Button registerButton = new Button("Registrieren");
        Button loginButton = new Button("Login");

        // 4. Status Anzeige
        statusLabel = new Label("Bereit...");
        statusLabel.setStyle("-fx-text-fill: grey;");

        // PING
        pingButton.setOnAction(e -> {
            String url = urlField.getText();
            chatService.setBaseUrl(url); // URL im Service setzen

            runAsync(() -> {
                boolean reachable = chatService.ping();
                updateStatus(reachable ? "Server erreichbar!" : "Server NICHT erreichbar!", reachable);
            });
        });

        // REGISTRIEREN
        registerButton.setOnAction(e -> {
            String url = urlField.getText();
            String user = userField.getText();
            String pass = passField.getText();
            chatService.setBaseUrl(url);

            runAsync(() -> {
                try {
                    String result = chatService.register(user, pass);
                    updateStatus("Registriert als: " + result, true);
                } catch (Exception ex) {
                    updateStatus("Fehler: " + ex.getMessage(), false);
                }
            });
        });

        // LOGIN
        loginButton.setOnAction(e -> {
            String url = urlField.getText();
            String user = userField.getText();
            String pass = passField.getText();
            chatService.setBaseUrl(url);

            runAsync(() -> {
                try {
                    boolean success = chatService.login(user, pass);
                    if (success) {
                        updateStatus("Login erfolgreich!", true);
                        // Wechsel zum Chat-Fenster
                        Platform.runLater(() -> showChatWindow(primaryStage, user));
                    } else {
                        updateStatus("Login fehlgeschlagen.", false);
                    }
                } catch (Exception ex) {
                    updateStatus("Fehler: " + ex.getMessage(), false);
                }
            });
        });

        root.getChildren().addAll(
                urlLabel, urlField, pingButton,
                new Separator(),
                new Label("User Data:"), userField, passField,
                new Separator(),
                registerButton, loginButton,
                new Separator(),
                statusLabel
        );

        Scene scene = new Scene(root, 350, 450);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    // Hilfsmethode: Führt Netzwerk-Code in einem eigenen Thread aus (damit GUI nicht einfriert)
    private void runAsync(Runnable task) {
        new Thread(task).start();
    }

    // Hilfsmethode: Aktualisiert das Label
    private void updateStatus(String text, boolean success) {
        Platform.runLater(() -> {
            statusLabel.setText(text);
            statusLabel.setStyle(success ? "-fx-text-fill: green;" : "-fx-text-fill: red;");
        });
    }

    public static void main(String[] args) {
        launch(args);
    }

    private volatile boolean isRunning = true; // Stoppt den Poller beim Logout

    private void showChatWindow(Stage stage, String myUsername) {
        stage.setTitle("Chat Client - Angemeldet als: " + myUsername);

        // --- UI Elemente ---
        TextArea chatArea = new TextArea();
        chatArea.setEditable(false); // Nur lesen
        chatArea.setWrapText(true);

        TextField recipientField = new TextField();
        recipientField.setPromptText("Empfänger (Name)");

        Button checkButton = new Button("Online?");

        // Status Anzeige (ein Label, das die Farbe wechselt)
        Label statusIndicator = new Label("");
        statusIndicator.setMinWidth(60); // Platz reservieren
        statusIndicator.setStyle("-fx-font-weight: bold;");

        // Aktion für den Button
        checkButton.setOnAction(e -> {
            String name = recipientField.getText();
            if (name == null || name.trim().isEmpty()) return;

            // UI Feedback: "Ich arbeite..."
            statusIndicator.setText("...");
            statusIndicator.setStyle("-fx-text-fill: black;");

            // Im Hintergrund prüfen
            runAsync(() -> {
                boolean online = chatService.isUserOnline(name);
                Platform.runLater(() -> {
                    if (online) {
                        statusIndicator.setText("✔ Online");
                        statusIndicator.setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
                    } else {
                        statusIndicator.setText("X Offline");
                        statusIndicator.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
                    }
                });
            });
        });

        javafx.scene.layout.HBox recipientBox = new javafx.scene.layout.HBox(10, recipientField, checkButton, statusIndicator);
        recipientBox.setAlignment(Pos.CENTER_LEFT);

        TextField messageField = new TextField();
        messageField.setPromptText("Deine Nachricht...");

        Button sendButton = new Button("Senden");

        // Enter-Taste zum Senden im Nachrichtenfeld
        messageField.setOnAction(event -> sendButton.fire());            // Simuliert einen Klick auf den Senden-Button

        // Wir packen die recipientBox und das messageField untereinander
        VBox inputBox = new VBox(10,
                new Label("An wen schreiben?"),
                recipientBox,
                new Label("Nachricht:"),
                messageField,
                sendButton
        );
        inputBox.setPadding(new Insets(10));

        Button logoutButton = new Button("Logout");

        // Hauptlayout
        javafx.scene.layout.BorderPane root = new javafx.scene.layout.BorderPane();
        root.setTop(logoutButton);
        root.setCenter(chatArea);
        root.setBottom(inputBox);
        BorderPane.setMargin(logoutButton, new Insets(5));

        // --- Logik ---

        // SENDEN
        sendButton.setOnAction(e -> {
            String target = recipientField.getText();
            String text = messageField.getText();
            if (target.isEmpty() || text.isEmpty()) return;

            runAsync(() -> {
                try {
                    boolean sent = chatService.sendMessage(target, text);
                    if (sent) {
                        Platform.runLater(() -> {
                            chatArea.appendText("Ich an " + target + ": " + text + "\n");
                            messageField.clear();
                        });
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            });
        });

        // LOGOUT
        logoutButton.setOnAction(e -> {
            isRunning = false; // Polling stoppen
            runAsync(() -> chatService.logout());
            // Zurück zum Login Screen
            try { start(stage); } catch (Exception ex) {}
        });

        // POLLING - Endlosschleife im Hintergrund
        Thread poller = new Thread(() -> {
            while (isRunning) {
                try {
                    // Jede Sekunde abfragen
                    Thread.sleep(1000);

                    var messages = chatService.pollMessages();

                    if (!messages.isEmpty()) {
                        Platform.runLater(() -> {
                            for (var msg : messages) {
                                chatArea.appendText(msg.getUsername() + ": " + msg.getMessage() + "\n");
                            }
                        });
                    }
                } catch (Exception ex) {
                    // Fehler beim Pollen ignorieren
                }
            }
        });
        poller.setDaemon(true); // Beendet Thread wenn Fenster zugeht
        poller.start();

        // Szene wechseln
        Scene chatScene = new Scene(root, 400, 500);
        stage.setScene(chatScene);
    }

}

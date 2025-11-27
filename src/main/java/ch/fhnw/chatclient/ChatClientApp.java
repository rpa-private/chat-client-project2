package ch.fhnw.chatclient;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
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
                        updateStatus("Login erfolgreich! Token erhalten.", true);
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
}

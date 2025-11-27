package ch.fhnw.chatclient;

import javafx.application.Application;
import javafx.stage.Stage;

public class ChatClientApp extends Application {
    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Chat Client");
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

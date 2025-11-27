module ch.fhnw.chatclient {
    requires javafx.controls;
    requires javafx.fxml;
    requires com.fasterxml.jackson.annotation;
    requires com.fasterxml.jackson.databind;
    requires java.net.http;


    opens ch.fhnw.chatclient to javafx.graphics, javafx.fxml;
    opens ch.fhnw.model to com.fasterxml.jackson.databind;
    exports ch.fhnw.chatclient;
}
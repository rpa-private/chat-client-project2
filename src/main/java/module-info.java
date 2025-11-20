module ch.fhnw.chatclient {
    requires javafx.controls;
    requires javafx.fxml;


    opens ch.fhnw.chatclient to javafx.fxml;
    exports ch.fhnw.chatclient;
}
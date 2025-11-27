package ch.fhnw.model;

import com.fasterxml.jackson.annotation.JsonInclude;

// @JsonInclude(JsonInclude.Include.NON_NULL) sorgt dafür, dass Felder, die null sind
// (z.B. token beim Empfangen), nicht ins JSON geschrieben werden.
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Message {
    private String token;
    private String username; // Beim Senden: Empfänger, Beim Empfangen: Absender
    private String message;

    public Message() {}

    public Message(String token, String username, String message) {
        this.token = token;
        this.username = username;
        this.message = message;
    }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    @Override
    public String toString() {
        return username + ": " + message; // Praktisch für Listen-Anzeige später
    }
}

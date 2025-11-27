package ch.fhnw.model;

public class ChatEntry {
    private String contact;
    private boolean outgoing;
    private String message;
    private long timestamp;

    public ChatEntry() {}

    public ChatEntry(String contact, boolean outgoing, String message, long timestamp) {
        this.contact = contact;
        this.outgoing = outgoing;
        this.message = message;
        this.timestamp = timestamp;
    }

    public String getContact() {
        return contact;
    }

    public void setContact(String contact) {
        this.contact = contact;
    }

    public boolean isOutgoing() {
        return outgoing;
    }

    public void setOutgoing(boolean outgoing) {
        this.outgoing = outgoing;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}

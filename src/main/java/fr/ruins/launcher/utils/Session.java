package fr.ruins.launcher.utils;

public class Session {
    private String uuid;
    private String username;
    private String sessionToken;
    private String email;

    public Session(String uuid, String username, String sessionToken, String email) {
        this.uuid = uuid;
        this.username = username;
        this.sessionToken = sessionToken;
        this.email = email;
    }

    public String getUsername() {
        return this.username;
    }

    public String getUuid() {
        return uuid;
    }

    public String getSessionToken() {
        return sessionToken;
    }

    public String getEmail() {
        return email;
    }
}
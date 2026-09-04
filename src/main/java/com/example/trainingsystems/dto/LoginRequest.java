package com.example.trainingsystems.dto;

public class LoginRequest {
    private String email;
    private String identifier;
    private String password;

    public String getEmail() {
        return email;
    }

    public String getIdentifier() {
        if (identifier != null && !identifier.isBlank()) {
            return identifier;
        }
        return email;
    }

    public String getPassword() {
        return password;
    }
}

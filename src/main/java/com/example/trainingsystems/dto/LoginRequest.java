package com.example.trainingsystems.dto;

public class LoginRequest {

    /*
     * 新版可以接收 Email 或 accountId。
     */
    private String identifier;

    /*
     * 保留 email，讓目前 Flutter 傳送的舊格式仍可使用。
     */
    private String email;

    private String password;

    public String getIdentifier() {
        if (identifier != null &&
            !identifier.isBlank()) {
            return identifier;
        }

        return email;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
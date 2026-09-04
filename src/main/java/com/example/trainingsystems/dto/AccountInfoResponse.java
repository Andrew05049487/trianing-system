package com.example.trainingsystems.dto;

public record AccountInfoResponse(
    Long userId,
    String name,
    String email,
    String accountId,
    String role,
    String bindingCode,
    String friendCode,
    boolean googleLinked,
    String googleEmail,
    boolean hasPassword
) {
}

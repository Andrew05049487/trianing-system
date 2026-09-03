package com.example.trainingsystems.dto;

import lombok.Data;

@Data
public class FriendRequestCreateDto {

    private Long senderId;

    private String friendCode;
}
package com.example.trainingsystems.dto;

import lombok.Data;

@Data
public class BindingRequest {

    private Long linkedUserId;

    private String bindingCode;

    private String relationship;
}
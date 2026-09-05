package com.example.trainingsystems.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "users")
@Data
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
        nullable = false,
        unique = true
    )
    private String email;

    @Column
    private String password;

    private String name;

    private String goal;

    @Column(
        nullable = false,
        length = 20
    )
    private String role = "PATIENT";

    @Column(
        name = "binding_code",
        length = 12
    )
    private String bindingCode;

    @Column(
        name = "friend_code",
        unique = true,
        length = 12
    )
    private String friendCode;

    @Column(
        name = "account_id",
        length = 50
    )
    private String accountId;

    @Column(
        name = "google_subject",
        length = 255
    )
    private String googleSubject;
}
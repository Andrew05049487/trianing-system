package com.example.trainingsystems.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString;

@Entity
@Table(name = "users")
@Data
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "account_id", length = 20)
    private String accountId;

    // Nullable for Google-only patient accounts.
    @Column
    @JsonIgnore
    @ToString.Exclude
    private String password;

    @Column(name = "google_subject", length = 255)
    @JsonIgnore
    @ToString.Exclude
    private String googleSubject;

    private String name;

    private String goal;

    @Column(nullable = false, length = 20)
    private String role = "PATIENT";

    @Column(name = "binding_code", length = 12)
    private String bindingCode;

    @Column(name = "friend_code", unique = true, length = 12)
    private String friendCode;
}

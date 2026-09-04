package com.example.trainingsystems.service;

public interface GoogleIdentityVerifier {
    VerifiedGoogleIdentity verify(String idToken);
}

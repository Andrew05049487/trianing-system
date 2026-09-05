package com.example.trainingsystems.controller;

import com.example.trainingsystems.dto.BindingRequest;
import com.example.trainingsystems.entity.User;
import com.example.trainingsystems.entity.UserBinding;
import com.example.trainingsystems.repository.UserBindingRepository;
import com.example.trainingsystems.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/bindings")
@CrossOrigin
public class BindingController {

    private final UserRepository userRepository;
    private final UserBindingRepository userBindingRepository;

    public BindingController(
            UserRepository userRepository,
            UserBindingRepository userBindingRepository) {
        this.userRepository = userRepository;
        this.userBindingRepository = userBindingRepository;
    }

    @PostMapping("/bind")
    public ResponseEntity<?> bind(@RequestBody BindingRequest request) {

        if (request.getLinkedUserId() == null
                || request.getBindingCode() == null
                || request.getRelationship() == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "綁定資料不完整"));
        }

        String bindingCode =
                request.getBindingCode().trim().toUpperCase(Locale.ROOT);

        String relationship =
                request.getRelationship().trim().toUpperCase(Locale.ROOT);

        if (!relationship.equals("THERAPIST")
                && !relationship.equals("FAMILY")) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "關係必須是 THERAPIST 或 FAMILY"));
        }

        User patient = userRepository
                .findByBindingCode(bindingCode)
                .orElse(null);

        if (patient == null) {
            return ResponseEntity.status(404)
                    .body(Map.of("message", "找不到此綁定碼"));
        }

        User linkedUser = userRepository
                .findById(request.getLinkedUserId())
                .orElse(null);

        if (linkedUser == null) {
            return ResponseEntity.status(404)
                    .body(Map.of("message", "找不到要綁定的帳號"));
        }

        if (patient.getId().equals(linkedUser.getId())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "不能綁定自己的帳號"));
        }

        boolean alreadyBound =
                userBindingRepository.existsByPatient_IdAndLinkedUser_Id(
                        patient.getId(),
                        linkedUser.getId()
                );

        if (alreadyBound) {
            return ResponseEntity.status(409)
                    .body(Map.of("message", "這兩個帳號已經綁定"));
        }

        UserBinding binding = new UserBinding();
        binding.setPatient(patient);
        binding.setLinkedUser(linkedUser);
        binding.setRelationship(relationship);

        userBindingRepository.save(binding);

        linkedUser.setRole(relationship);
        userRepository.save(linkedUser);

        return ResponseEntity.ok(Map.of(
                "message", "帳號綁定成功",
                "patientId", patient.getId(),
                "patientName", patient.getName(),
                "relationship", relationship
        ));
    }
}
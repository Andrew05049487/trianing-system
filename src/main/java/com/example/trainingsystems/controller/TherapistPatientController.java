package com.example.trainingsystems.controller;

import com.example.trainingsystems.dto.BindTherapistPatientRequest;
import com.example.trainingsystems.dto.TherapistPatientDto;
import com.example.trainingsystems.dto.TherapistPatientLookupDto;
import com.example.trainingsystems.service.TherapistPatientBindingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/therapist/patients")
public class TherapistPatientController {
    private final TherapistPatientBindingService service;

    public TherapistPatientController(TherapistPatientBindingService service) {
        this.service = service;
    }

    @GetMapping
    public List<TherapistPatientDto> getPatients(
        @RequestHeader("X-User-Id") Long therapistId,
        @RequestHeader("X-Custom-Exercise-Token") String identityToken
    ) {
        return service.getPatients(therapistId, identityToken);
    }

    @GetMapping("/lookup")
    public TherapistPatientLookupDto lookupPatient(
        @RequestParam String bindingCode,
        @RequestHeader("X-User-Id") Long therapistId,
        @RequestHeader("X-Custom-Exercise-Token") String identityToken
    ) {
        return service.lookupPatient(bindingCode, therapistId, identityToken);
    }

    @PostMapping("/bind")
    public TherapistPatientDto bindPatient(
        @RequestBody BindTherapistPatientRequest request,
        @RequestHeader("X-User-Id") Long therapistId,
        @RequestHeader("X-Custom-Exercise-Token") String identityToken
    ) {
        return service.bindPatient(
            request.getBindingCode(),
            therapistId,
            identityToken
        );
    }

    @DeleteMapping("/{patientId}")
    public ResponseEntity<Void> unbindPatient(
        @PathVariable Long patientId,
        @RequestHeader("X-User-Id") Long therapistId,
        @RequestHeader("X-Custom-Exercise-Token") String identityToken
    ) {
        service.unbindPatient(patientId, therapistId, identityToken);
        return ResponseEntity.noContent().build();
    }
}

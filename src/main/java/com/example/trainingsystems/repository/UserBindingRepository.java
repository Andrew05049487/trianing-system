package com.example.trainingsystems.repository;

import com.example.trainingsystems.entity.UserBinding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserBindingRepository
        extends JpaRepository<UserBinding, Long> {

    boolean existsByPatient_IdAndLinkedUser_Id(
        Long patientId,
        Long linkedUserId
    );

    List<UserBinding> findByPatient_IdOrLinkedUser_Id(
        Long patientId,
        Long linkedUserId
    );

    boolean existsByPatient_IdAndLinkedUser_IdAndRelationshipIgnoreCase(
        Long patientId,
        Long linkedUserId,
        String relationship
    );

    List<UserBinding> findAllByLinkedUser_IdAndRelationshipIgnoreCase(
        Long linkedUserId,
        String relationship
    );

    Optional<UserBinding>
        findByPatient_IdAndLinkedUser_IdAndRelationshipIgnoreCase(
            Long patientId,
            Long linkedUserId,
            String relationship
        );
}
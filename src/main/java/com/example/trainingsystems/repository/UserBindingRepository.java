package com.example.trainingsystems.repository;

import com.example.trainingsystems.entity.UserBinding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

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
}

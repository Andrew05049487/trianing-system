package com.example.trainingsystems.repository;

import com.example.trainingsystems.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByGoogleSubject(String googleSubject);

    Optional<User> findByBindingCode(String bindingCode);

    Optional<User> findByFriendCode(String friendCode);
}

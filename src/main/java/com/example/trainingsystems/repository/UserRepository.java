package com.example.trainingsystems.repository;

import com.example.trainingsystems.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository
        extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByBindingCode(String bindingCode);

    Optional<User> findByFriendCode(String friendCode);

    Optional<User> findByFriendCodeIgnoreCase(String friendCode);

    Optional<User> findByAccountId(String accountId);

    Optional<User> findByGoogleSubject(String googleSubject);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from User user where user.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);
}

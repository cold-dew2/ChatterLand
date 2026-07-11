package com.example.backend.chld.repository;

import com.example.backend.chld.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    // user_id 존재 여부 확인
    boolean existsByUserId(String userId);

    // user_id 존재 여부 확인
    Optional<User> findByUserId(String userId);
}
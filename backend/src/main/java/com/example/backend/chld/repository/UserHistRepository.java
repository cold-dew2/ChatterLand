package com.example.backend.chld.repository;

import com.example.backend.chld.entity.UserHist;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserHistRepository extends JpaRepository<UserHist, Long> {

}
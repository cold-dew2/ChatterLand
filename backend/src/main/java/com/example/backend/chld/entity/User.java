package com.example.backend.chld.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "tb_chld_user_info")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @Column(name = "user_id", length = 100, unique = true, nullable = false)
    private String userId;

    @Column(name = "user_pw", length = 255, nullable = false)
    private String userPw;

    @Column(name = "user_nm", length = 100)
    private String userNm;

    @Column(length = 100)
    private String email;

    @Column(name = "state_cd", length = 50)
    private String stateCd;

    @Column(name = "role_cd", length = 50)
    private String roleCd;

    @Column(name = "birth_dt")
    private LocalDate birthDt;

    @Column(name = "gender_cd", length = 50)
    private String genderCd;

    @Column(name = "phone_num", length = 20)
    private String phoneNum;

    @UpdateTimestamp
    @Column(name = "created_dt", updatable = false)
    private LocalDateTime createdDt;

    @UpdateTimestamp
    @Column(name = "updated_dt")
    private LocalDateTime updatedDt;
}
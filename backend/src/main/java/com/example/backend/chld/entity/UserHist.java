package com.example.backend.chld.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "tb_chld_login_hist")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserHist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "login_seq")
    private Long loginSeq;

    @Column(name = "user_id", length = 100, unique = true, nullable = false)
    private String userId;

    @UpdateTimestamp
    @Column(name = "login_dt")
    private LocalDateTime loginDt;
}
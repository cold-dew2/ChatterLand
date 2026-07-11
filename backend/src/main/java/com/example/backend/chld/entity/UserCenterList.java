package com.example.backend.chld.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "tb_chld_user_center_list")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserCenterList {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "center_seq")
    private Long centerSeq;

    @Column(name = "user_id", length = 100, unique = true, nullable = false)
    private String userId;

    @Column(name = "center_id", length = 100, unique = true, nullable = false)
    private String centerId;

    @Column(name = "state_cd", length = 50, unique = true, nullable = false)
    private String stateCd;

    @UpdateTimestamp
    @Column(name = "created_dt", updatable = false)
    private LocalDateTime createdDt;

    @UpdateTimestamp
    @Column(name = "updated_dt")
    private LocalDateTime updatedDt;
}
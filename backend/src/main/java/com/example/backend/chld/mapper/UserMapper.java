package com.example.backend.chld.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.Map;

@Mapper
public interface UserMapper {
    Map<String, Object> findByEmail(@Param("email") String email);
    Map<String, Object> findById(@Param("userId") long userId);
    Long findStudentIdByUserId(@Param("userId") long userId);
    int insertUser(@Param("role") String role, @Param("centerId") long centerId,
                   @Param("name") String name, @Param("email") String email,
                   @Param("passwordHash") String passwordHash, @Param("age") Integer age,
                   @Param("termsAgreed") boolean termsAgreed);
    int insertStudentProfile(@Param("userId") long userId, @Param("centerId") long centerId,
                             @Param("name") String name, @Param("age") Integer age);
    int insertRefreshToken(@Param("userId") long userId, @Param("hash") String hash,
                           @Param("expiresAt") LocalDateTime expiresAt);
    Map<String, Object> findActiveRefreshToken(@Param("hash") String hash);
    int revokeRefreshToken(@Param("hash") String hash);
    java.util.List<String> findEmailsByNameCenterRole(@Param("name") String name, @Param("centerId") long centerId, @Param("role") String role);
    int updatePassword(@Param("userId") long userId, @Param("passwordHash") String passwordHash);
    int revokeAllRefreshTokens(@Param("userId") long userId);
}

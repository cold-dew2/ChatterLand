package com.example.backend.chld.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Map;

@Mapper
public interface PasswordResetMapper {
    long countRequestsSince(@Param("userId") long userId, @Param("minutes") int minutes);
    int invalidateOpenRequests(@Param("userId") long userId);
    int insertRequest(@Param("userId") long userId, @Param("codeHash") String codeHash, @Param("expiresMinutes") int expiresMinutes);
    Map<String,Object> findActiveCodeRequest(@Param("userId") long userId);
    int incrementAttempts(@Param("requestId") long requestId);
    int invalidateRequest(@Param("requestId") long requestId);
    int markVerified(@Param("requestId") long requestId, @Param("tokenHash") String tokenHash, @Param("tokenMinutes") int tokenMinutes);
    Map<String,Object> findValidResetToken(@Param("tokenHash") String tokenHash);
    int markUsed(@Param("requestId") long requestId);
}

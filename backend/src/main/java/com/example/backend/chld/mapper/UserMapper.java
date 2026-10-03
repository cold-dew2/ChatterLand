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
    /** sessionId: 로그인 1회(기기)마다 만드는 세션 ID. 갱신(회전)으로 만든 refresh token도 같은 세션 ID를 이어받는다. */
    int insertRefreshToken(@Param("userId") long userId, @Param("hash") String hash,
                           @Param("expiresAt") LocalDateTime expiresAt, @Param("sessionId") String sessionId);
    /** refresh token이 속한 세션 ID(폐기 여부와 무관). 이 기능 이전에 발급된 토큰은 null. */
    String findRefreshSessionId(@Param("hash") String hash);
    /** 로그인 세션 하나(기기)의 refresh token을 모두 폐기한다. */
    int revokeRefreshSession(@Param("sessionId") String sessionId);
    /** access token 서버 상태: 1(유효) / 0(버전 불일치·세션 폐기) / null(없거나 비활성 계정) */
    Integer accessTokenState(@Param("userId") long userId, @Param("tokenVersion") int tokenVersion, @Param("sessionId") String sessionId);
    String findPasswordHash(@Param("userId") long userId);

    // ── 비밀번호 변경 시도 제한 ──
    /** 같은 사용자의 비밀번호 변경 요청을 한 번에 하나씩 처리하도록 사용자 행을 잠근다(트랜잭션 안에서 호출). */
    Long lockUserForUpdate(@Param("userId") long userId);
    int countPasswordChangeFailuresSince(@Param("userId") long userId, @Param("windowSeconds") long windowSeconds);
    /** 기간 안 가장 오래된 실패가 기간 밖으로 나갈 때까지 남은 초(다시 시도할 수 있는 시각) */
    Long secondsUntilPasswordChangeRetry(@Param("userId") long userId, @Param("windowSeconds") long windowSeconds);
    int insertPasswordChangeFailure(@Param("userId") long userId);
    int deletePasswordChangeFailures(@Param("userId") long userId);

    // ── 정리 작업 ──
    int deleteExpiredRefreshTokens(@Param("limit") int limit);
    int deleteRevokedRefreshTokens(@Param("retentionSeconds") long retentionSeconds, @Param("limit") int limit);
    int deleteOldPasswordChangeFailures(@Param("olderThanSeconds") long olderThanSeconds, @Param("limit") int limit);
    Map<String, Object> findActiveRefreshToken(@Param("hash") String hash);
    int revokeRefreshToken(@Param("hash") String hash);
    java.util.List<String> findEmailsByNameCenterRole(@Param("name") String name, @Param("centerId") long centerId, @Param("role") String role);
    /** 비밀번호를 바꾸고 token_version을 1 올려 이미 발급된 access token을 무효화한다. */
    int updatePassword(@Param("userId") long userId, @Param("passwordHash") String passwordHash);
    Integer findTokenVersion(@Param("userId") long userId);
    int revokeAllRefreshTokens(@Param("userId") long userId);
}

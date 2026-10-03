package com.example.backend.chld.service.impl;

import com.example.backend.chld.dto.request.LoginRequest;
import com.example.backend.chld.dto.request.PasswordChangeRequest;
import com.example.backend.chld.dto.request.RefreshRequest;
import com.example.backend.chld.dto.request.SignupRequest;
import com.example.backend.chld.dto.response.AuthResponse;
import com.example.backend.chld.dto.response.TokenResponse;
import com.example.backend.chld.dto.response.UserResponse;
import com.example.backend.chld.mapper.UserMapper;
import com.example.backend.chld.service.AuthService;
import com.example.backend.chld.service.CenterService;
import com.example.backend.chld.service.ConsentService;
import com.example.backend.global.jwt.JwtUtil;
import com.example.backend.global.jwt.TokenPrincipal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class AuthServiceImpl implements AuthService {
    private static final long REFRESH_TTL_DAYS = 30;
    private final UserMapper users;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final SecureRandom secureRandom = new SecureRandom();
    private final int refreshBytes;
    private final CenterService centers;
    private final ConsentService consents;
    static final int CLEANUP_BATCH = 1000;
    private final int maxPasswordChangeFailures;
    private final long passwordChangeWindowSeconds;
    private final long revokedRetentionSeconds;

    public AuthServiceImpl(UserMapper users, PasswordEncoder passwordEncoder, JwtUtil jwtUtil, CenterService centers, ConsentService consents,
                           @Value("${auth.refresh-token-bytes:48}") int refreshBytes,
                           @Value("${app.auth.password-change.max-failures:5}") int maxPasswordChangeFailures,
                           @Value("${app.auth.password-change.failure-window:PT15M}") Duration passwordChangeWindow,
                           @Value("${app.auth.refresh-token.revoked-retention:P7D}") Duration revokedRetention) {
        this.maxPasswordChangeFailures = Math.max(1, maxPasswordChangeFailures);
        this.passwordChangeWindowSeconds = Math.max(1, passwordChangeWindow.toSeconds());
        this.revokedRetentionSeconds = Math.max(0, revokedRetention.toSeconds());
        this.centers = centers;
        this.consents = consents;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.refreshBytes = refreshBytes;
    }

    @Override
    @Transactional
    public UserResponse signup(SignupRequest request) {
        String role = request.role().toUpperCase(Locale.ROOT);
        if (!role.equals("STUDENT") && !role.equals("TEACHER")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "역할을 확인해 주세요.");
        if (!Boolean.TRUE.equals(request.termsAgreed())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "약관 동의가 필요합니다.");
        if (role.equals("STUDENT") && (request.age() == null || request.age() < 1 || request.age() > 18))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "학생 나이를 확인해 주세요.");
        centers.requireActiveCenter(request.centerId());
        consents.validateSignupConsents(role, request.age(), request.consents());
        if (users.findByEmail(request.email()) != null) throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다.");
        int inserted = users.insertUser(role, request.centerId(), request.name().trim(), request.email().trim(),
                passwordEncoder.encode(request.password()), request.age(), true);
        if (inserted != 1) throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "계정을 생성하지 못했습니다.");
        Map<String, Object> user = users.findByEmail(request.email());
        long userId = number(user, "userId");
        if (role.equals("STUDENT")) users.insertStudentProfile(userId, request.centerId(), request.name().trim(), request.age());
        consents.recordSignupConsents(userId, role, request.age(), request.consents());
        return toUser(users.findById(userId));
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        Map<String, Object> user = users.findByEmail(request.email().trim());
        if (user == null || !passwordEncoder.matches(request.password(), String.valueOf(user.get("passwordHash")))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호를 확인해 주세요.");
        }
        long userId = number(user, "userId");
        String role = String.valueOf(user.get("role"));
        TokenResponse tokens = startSession(userId, role, tokenVersion(user));
        return new AuthResponse(tokens.accessToken(), tokens.refreshToken(), toUser(users.findById(userId)));
    }

    @Override
    @Transactional
    public TokenResponse refresh(RefreshRequest request) {
        String oldHash = hash(request.refreshToken());
        Map<String, Object> record = users.findActiveRefreshToken(oldHash);
        if (record == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "세션이 만료되었습니다.");
        if (users.revokeRefreshToken(oldHash) != 1)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "세션이 이미 갱신되었거나 만료되었습니다.");
        long userId = number(record, "userId");
        String role = String.valueOf(record.get("role"));
        // 회전한 refresh token은 같은 로그인 세션을 이어받는다(세션 ID가 없던 이전 토큰이면 새로 만든다).
        String sessionId = record.get("sessionId") == null ? UUID.randomUUID().toString() : String.valueOf(record.get("sessionId"));
        String nextRefresh = newRefreshToken();
        users.insertRefreshToken(userId, hash(nextRefresh), LocalDateTime.now().plusDays(REFRESH_TTL_DAYS), sessionId);
        return new TokenResponse(jwtUtil.createAccessToken(userId, role, tokenVersion(record), sessionId), nextRefresh);
    }

    @Override
    @Transactional
    public void logout(String refreshToken, String principalSessionId) {
        // 이 기기의 로그인 세션 전체(회전된 refresh token 포함)를 폐기한다. 폐기된 세션의 access token은 JWT 필터가 즉시 거절한다.
        if (refreshToken != null && !refreshToken.isBlank()) {
            String tokenHash = hash(refreshToken);
            String sessionId = users.findRefreshSessionId(tokenHash);
            if (sessionId != null) users.revokeRefreshSession(sessionId);
            users.revokeRefreshToken(tokenHash);
        }
        // refresh token 없이(또는 다른 값과 함께) 와도 access token의 세션은 끊는다.
        if (principalSessionId != null) users.revokeRefreshSession(principalSessionId);
    }

    /**
     * 실패 기록(현재 비밀번호 틀림)은 오류 응답과 함께 커밋해야 하므로 ResponseStatusException으로는 롤백하지 않는다.
     * 쓰기는 모든 검사를 통과한 뒤에만 한다(실패 기록 제외).
     */
    @Override
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public TokenResponse changePassword(TokenPrincipal principal, PasswordChangeRequest request) {
        if (principal == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "인증이 필요합니다.");
        if (!request.newPassword().equals(request.newPasswordConfirm()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "새 비밀번호와 확인 값이 일치하지 않아요.");
        // 같은 사용자의 요청을 차례로 처리해, 동시에 여러 번 시도해도 허용 횟수를 넘지 못하게 한다.
        if (users.lockUserForUpdate(principal.userId()) == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "계정을 찾을 수 없습니다.");
        int failures = users.countPasswordChangeFailuresSince(principal.userId(), passwordChangeWindowSeconds);
        if (failures >= maxPasswordChangeFailures) throw tooManyPasswordChangeFailures(principal.userId());
        String currentHash = users.findPasswordHash(principal.userId());
        if (currentHash == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "계정을 찾을 수 없습니다.");
        // 현재 비밀번호가 틀려도 401을 쓰지 않는다(화면이 세션 만료로 처리해 로그아웃시키지 않도록 400·429).
        if (!passwordEncoder.matches(request.currentPassword(), currentHash)) {
            users.insertPasswordChangeFailure(principal.userId());
            int remaining = maxPasswordChangeFailures - failures - 1;
            if (remaining <= 0) {
                log.warn("Password change locked after {} failed attempts", maxPasswordChangeFailures);
                throw tooManyPasswordChangeFailures(principal.userId());
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "현재 비밀번호가 올바르지 않아요. (남은 시도 " + remaining + "회)");
        }
        if (passwordEncoder.matches(request.newPassword(), currentHash))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "현재 비밀번호와 다른 새 비밀번호를 입력해 주세요.");
        // 재설정과 같은 정책: token_version을 올리고 refresh token을 모두 폐기해 다른 기기의 세션을 끊는다.
        if (users.updatePassword(principal.userId(), passwordEncoder.encode(request.newPassword())) != 1)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "계정을 찾을 수 없습니다.");
        users.revokeAllRefreshTokens(principal.userId());
        users.deletePasswordChangeFailures(principal.userId());
        // 변경한 이 기기는 로그인을 유지하도록 새 세션의 토큰을 발급한다.
        Integer version = users.findTokenVersion(principal.userId());
        return startSession(principal.userId(), principal.role(), version == null ? 0 : version);
    }

    private ResponseStatusException tooManyPasswordChangeFailures(long userId) {
        Long seconds = users.secondsUntilPasswordChangeRetry(userId, passwordChangeWindowSeconds);
        long minutes = Math.max(1, (seconds == null ? passwordChangeWindowSeconds : seconds) + 59) / 60;
        return new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                "현재 비밀번호를 여러 번 틀렸어요. " + minutes + "분 뒤에 다시 시도해 주세요.");
    }

    /**
     * 오래된 인증 기록 정리(정기 작업). 만료된 refresh token, 폐기된 지 보관 기간이 지난 refresh token,
     * 시도 제한 기간이 지난 비밀번호 변경 실패 기록을 지운다. 폐기되지 않고 만료 전인 행(현재 로그인 세션)은 지우지 않는다.
     * 한 번에 CLEANUP_BATCH건씩 지워 잠금을 짧게 유지한다(트랜잭션으로 묶지 않음).
     */
    @Override
    public int purgeStaleAuthRecords() {
        int deleted = 0;
        deleted += drain(() -> users.deleteExpiredRefreshTokens(CLEANUP_BATCH));
        deleted += drain(() -> users.deleteRevokedRefreshTokens(revokedRetentionSeconds, CLEANUP_BATCH));
        deleted += drain(() -> users.deleteOldPasswordChangeFailures(passwordChangeWindowSeconds, CLEANUP_BATCH));
        return deleted;
    }

    private static int drain(java.util.function.IntSupplier batch) {
        int total = 0;
        for (int round = 0; round < 1000; round++) {
            int deleted = batch.getAsInt();
            total += deleted;
            if (deleted < CLEANUP_BATCH) break;
        }
        return total;
    }

    /** 새 로그인 세션(기기)을 시작한다: 세션 ID를 만들고 refresh token을 저장한 뒤 같은 세션의 access token을 발급한다. */
    private TokenResponse startSession(long userId, String role, int tokenVersion) {
        String sessionId = UUID.randomUUID().toString();
        String refresh = newRefreshToken();
        users.insertRefreshToken(userId, hash(refresh), LocalDateTime.now().plusDays(REFRESH_TTL_DAYS), sessionId);
        return new TokenResponse(jwtUtil.createAccessToken(userId, role, tokenVersion, sessionId), refresh);
    }

    @Override
    public UserResponse currentUser(long userId) {
        Map<String, Object> user = users.findById(userId);
        if (user == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "계정을 찾을 수 없습니다.");
        return toUser(user);
    }

    @Override
    public boolean isAccessTokenActive(long userId, int tokenVersion, String sessionId) {
        Integer state = users.accessTokenState(userId, tokenVersion, sessionId);
        return state != null && state == 1;
    }

    private int tokenVersion(Map<String, Object> row) {
        return row.get("tokenVersion") instanceof Number version ? version.intValue() : 0;
    }

    private UserResponse toUser(Map<String, Object> user) {
        if (user == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "계정을 찾을 수 없습니다.");
        Object student = user.get("studentId");
        return new UserResponse(number(user, "userId"), String.valueOf(user.get("role")),
                String.valueOf(user.get("name")), String.valueOf(user.get("email")), number(user, "centerId"),
                student == null ? null : ((Number) student).longValue(), String.valueOf(user.get("centerName")));
    }

    private String newRefreshToken() {
        byte[] bytes = new byte[refreshBytes]; secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    private String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException("토큰을 처리하지 못했습니다.", e); }
    }
    private long number(Map<String, Object> map, String key) { return ((Number) map.get(key)).longValue(); }
}

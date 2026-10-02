package com.example.backend.chld.service.impl;

import com.example.backend.chld.dto.request.LoginRequest;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;

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

    public AuthServiceImpl(UserMapper users, PasswordEncoder passwordEncoder, JwtUtil jwtUtil, CenterService centers, ConsentService consents,
                           @Value("${auth.refresh-token-bytes:48}") int refreshBytes) {
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
        String refresh = newRefreshToken();
        users.insertRefreshToken(userId, hash(refresh), LocalDateTime.now().plusDays(REFRESH_TTL_DAYS));
        return new AuthResponse(jwtUtil.createAccessToken(userId, role), refresh, toUser(users.findById(userId)));
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
        String nextRefresh = newRefreshToken();
        users.insertRefreshToken(userId, hash(nextRefresh), LocalDateTime.now().plusDays(REFRESH_TTL_DAYS));
        return new TokenResponse(jwtUtil.createAccessToken(userId, role), nextRefresh);
    }

    @Override
    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) users.revokeRefreshToken(hash(refreshToken));
    }

    @Override
    public UserResponse currentUser(long userId) {
        Map<String, Object> user = users.findById(userId);
        if (user == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "계정을 찾을 수 없습니다.");
        return toUser(user);
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

package com.example.backend.chld.service.impl;

import com.example.backend.chld.dto.request.FindIdRequest;
import com.example.backend.chld.dto.request.PasswordResetConfirmRequest;
import com.example.backend.chld.dto.request.PasswordResetIdentityRequest;
import com.example.backend.chld.dto.request.PasswordResetRequest;
import com.example.backend.chld.dto.request.PasswordResetVerifyRequest;
import com.example.backend.chld.dto.response.FindIdResponse;
import com.example.backend.chld.dto.response.PasswordResetTokenResponse;
import com.example.backend.chld.mapper.PasswordResetMapper;
import com.example.backend.chld.mapper.UserMapper;
import com.example.backend.chld.service.AccountRecoveryService;
import com.example.backend.chld.service.CenterService;
import com.example.backend.chld.service.MailService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
public class AccountRecoveryServiceImpl implements AccountRecoveryService {
    static final int CODE_MINUTES = 10;
    static final int TOKEN_MINUTES = 15;
    static final int MAX_ATTEMPTS = 5;
    static final int RESEND_COOLDOWN_MINUTES = 1;
    static final int MAX_REQUESTS_PER_HOUR = 5;
    /** 가입 정보 본인 확인 시도(성공·실패 모두) 계정당 1시간 상한 */
    static final int MAX_IDENTITY_ATTEMPTS_PER_HOUR = 5;
    static final String IDENTITY_MISMATCH = "입력한 정보와 일치하는 계정을 찾지 못했어요. 이메일, 이름, 센터, 역할(학생은 나이)을 확인해 주세요.";

    private final UserMapper users;
    private final PasswordResetMapper resets;
    private final CenterService centers;
    private final MailService mail;
    private final PasswordEncoder passwordEncoder;
    private final byte[] codeKey;
    private final SecureRandom random = new SecureRandom();

    public AccountRecoveryServiceImpl(UserMapper users, PasswordResetMapper resets, CenterService centers, MailService mail,
                                      PasswordEncoder passwordEncoder, @Value("${jwt.secret}") String secret) {
        this.users = users; this.resets = resets; this.centers = centers; this.mail = mail; this.passwordEncoder = passwordEncoder;
        this.codeKey = ("password-reset:" + secret).getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public FindIdResponse findId(FindIdRequest request) {
        centers.requireActiveCenter(request.centerId());
        List<String> emails = users.findEmailsByNameCenterRole(request.name().trim(), request.centerId(), request.role());
        if (emails.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "입력한 정보와 일치하는 계정을 찾지 못했어요.");
        return new FindIdResponse(emails.stream().map(AccountRecoveryServiceImpl::maskEmail).toList());
    }

    @Override
    @Transactional
    public void requestPasswordReset(PasswordResetRequest request) {
        // 메일 설정 여부는 계정 조회 전에 확인해 가입 여부가 드러나지 않게 한다.
        mail.requireAvailable();
        Map<String,Object> user = users.findByEmail(request.email().trim());
        if (user == null) return;
        long userId = ((Number) user.get("userId")).longValue();
        // 재발급 제한: 1분 이내 재요청과 1시간 5회 초과는 조용히 무시한다(응답은 동일).
        if (resets.countRequestsSince(userId, RESEND_COOLDOWN_MINUTES) > 0 || resets.countRequestsSince(userId, 60) >= MAX_REQUESTS_PER_HOUR) return;
        String code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
        resets.invalidateOpenRequests(userId);
        resets.insertRequest(userId, hmac(code), CODE_MINUTES);
        mail.sendPasswordResetCode(String.valueOf(user.get("email")), code, CODE_MINUTES);
    }

    @Override
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public PasswordResetTokenResponse verifyResetCode(PasswordResetVerifyRequest request) {
        Map<String,Object> user = users.findByEmail(request.email().trim());
        Map<String,Object> active = user == null ? null : resets.findActiveCodeRequest(((Number) user.get("userId")).longValue());
        if (active == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "인증 코드가 만료되었거나 올바르지 않아요. 인증 코드를 다시 받아 주세요.");
        long requestId = ((Number) active.get("requestId")).longValue();
        int attempts = ((Number) active.get("attempts")).intValue();
        if (attempts >= MAX_ATTEMPTS) {
            resets.invalidateRequest(requestId);
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "인증 시도 횟수를 초과했어요. 인증 코드를 다시 받아 주세요.");
        }
        boolean matches = MessageDigest.isEqual(hmac(request.code()).getBytes(StandardCharsets.UTF_8),
                String.valueOf(active.get("codeHash")).getBytes(StandardCharsets.UTF_8));
        if (!matches) {
            resets.incrementAttempts(requestId);
            int remaining = MAX_ATTEMPTS - attempts - 1;
            if (remaining <= 0) {
                resets.invalidateRequest(requestId);
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "인증 시도 횟수를 초과했어요. 인증 코드를 다시 받아 주세요.");
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "인증 코드가 올바르지 않아요. (남은 시도 " + remaining + "회)");
        }
        String token = newToken();
        if (resets.markVerified(requestId, sha256(token), TOKEN_MINUTES) != 1)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 사용된 인증 코드예요. 인증 코드를 다시 받아 주세요.");
        return new PasswordResetTokenResponse(token, TOKEN_MINUTES * 60L);
    }

    /**
     * 메일 없이 가입 정보로 본인 확인(임시 방식). 이후 단계(confirmPasswordReset)는 메일 인증과 같다.
     * - 어느 항목이 틀렸는지, 이메일이 가입돼 있는지 알려 주지 않는다(같은 오류 문구).
     * - 계정당 1시간 5회까지(시도 기록은 password_reset_requests에 남기고 실패하면 바로 무효화). 실패 기록은 롤백하지 않는다.
     * - 새 요청을 만들 때 이전에 열려 있던 요청(메일 코드·재설정 토큰)은 모두 무효화한다.
     */
    @Override
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public PasswordResetTokenResponse verifyIdentity(PasswordResetIdentityRequest request) {
        Map<String,Object> user = users.findRecoveryIdentityByEmail(request.email().trim());
        if (user == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, IDENTITY_MISMATCH);
        long userId = ((Number) user.get("userId")).longValue();
        if (resets.countRequestsSince(userId, 60) >= MAX_IDENTITY_ATTEMPTS_PER_HOUR)
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "확인 시도가 너무 많아요. 1시간 뒤에 다시 시도해 주세요.");
        resets.invalidateOpenRequests(userId);
        resets.insertRequest(userId, hmac(newToken()), TOKEN_MINUTES); // 코드는 쓰지 않으므로 아무도 모르는 값
        Map<String,Object> attempt = resets.findActiveCodeRequest(userId);
        if (attempt == null) throw new IllegalStateException("재설정 요청을 만들지 못했습니다.");
        long requestId = ((Number) attempt.get("requestId")).longValue();
        String role = String.valueOf(user.get("role"));
        Number age = (Number) user.get("age");
        boolean matches = role.equals(request.role())
                && Objects.equals(((Number) user.get("centerId")).longValue(), request.centerId())
                && String.valueOf(user.get("name")).trim().equals(request.name().trim())
                && (!role.equals("STUDENT") || (age != null && request.age() != null && age.intValue() == request.age()));
        if (!matches) {
            resets.invalidateRequest(requestId);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, IDENTITY_MISMATCH);
        }
        String token = newToken();
        if (resets.markVerified(requestId, sha256(token), TOKEN_MINUTES) != 1)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "다른 곳에서 먼저 재설정을 시작했어요. 처음부터 다시 진행해 주세요.");
        return new PasswordResetTokenResponse(token, TOKEN_MINUTES * 60L);
    }

    @Override
    @Transactional
    public void confirmPasswordReset(PasswordResetConfirmRequest request) {
        if (!request.newPassword().equals(request.newPasswordConfirm()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "새 비밀번호와 확인 값이 일치하지 않아요.");
        Map<String,Object> reset = resets.findValidResetToken(sha256(request.resetToken()));
        if (reset == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "재설정 시간이 지났거나 이미 사용된 요청이에요. 처음부터 다시 진행해 주세요.");
        long requestId = ((Number) reset.get("requestId")).longValue();
        long userId = ((Number) reset.get("userId")).longValue();
        if (resets.markUsed(requestId) != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 사용된 재설정 요청이에요.");
        if (users.updatePassword(userId, passwordEncoder.encode(request.newPassword())) != 1)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "비밀번호를 변경할 수 없는 계정이에요.");
        // 다른 기기에 남은 로그인 세션을 모두 끊는다: updatePassword가 token_version을 올려 access token을 즉시 무효화하고,
        // refresh token도 모두 폐기해 다시 발급받지 못하게 한다.
        users.revokeAllRefreshTokens(userId);
        resets.invalidateOpenRequests(userId);
    }

    static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 0) return "***";
        String local = email.substring(0, at), domain = email.substring(at + 1);
        String maskedLocal = local.length() <= 2 ? local.charAt(0) + "*" : local.substring(0, 2) + "*".repeat(Math.min(6, local.length() - 2));
        int dot = domain.lastIndexOf('.');
        String maskedDomain = dot <= 0 ? "***" : domain.charAt(0) + "***" + domain.substring(dot);
        return maskedLocal + "@" + maskedDomain;
    }

    private String hmac(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(codeKey, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException("인증 코드를 처리하지 못했습니다.", e); }
    }

    private String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException("토큰을 처리하지 못했습니다.", e); }
    }

    private String newToken() {
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}

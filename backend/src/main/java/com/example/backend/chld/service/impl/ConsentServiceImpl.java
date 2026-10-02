package com.example.backend.chld.service.impl;

import com.example.backend.chld.dto.request.ConsentUpdateRequest;
import com.example.backend.chld.dto.request.SignupConsentRequest;
import com.example.backend.chld.dto.response.ConsentResponse;
import com.example.backend.chld.exception.ConsentRequiredException;
import com.example.backend.chld.mapper.ConsentMapper;
import com.example.backend.chld.service.AudioRetentionService;
import com.example.backend.chld.service.ConsentService;
import com.example.backend.global.jwt.TokenPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.example.backend.chld.service.ConsentPolicy.*;

@Service
public class ConsentServiceImpl implements ConsentService {
    private final ConsentMapper consents;
    private final AudioRetentionService retention;

    public ConsentServiceImpl(ConsentMapper consents, AudioRetentionService retention) {
        this.consents = consents; this.retention = retention;
    }

    @Override
    public void validateSignupConsents(String role, Integer age, SignupConsentRequest request) {
        if (request == null || !Boolean.TRUE.equals(request.privacy()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "개인정보 수집·이용(필수)에 동의해 주세요.");
        if (!CURRENT_VERSION.equals(request.policyVersion()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "동의 안내문이 갱신되었습니다. 화면을 새로고침한 뒤 다시 동의해 주세요.");
        if (guardianRequired(role, age)) requireGuardian(request.guardianConfirmed(), request.guardianName(), request.guardianRelation());
    }

    @Override
    @Transactional
    public void recordSignupConsents(long userId, String role, Integer age, SignupConsentRequest request) {
        validateSignupConsents(role, age, request);
        boolean guardian = guardianRequired(role, age);
        String guardianName = guardian ? request.guardianName().trim() : null;
        String relation = guardian ? request.guardianRelation().trim() : null;
        consents.upsertConsent(userId, PRIVACY, true, CURRENT_VERSION, guardianName, relation);
        if (guardian) consents.upsertConsent(userId, GUARDIAN, true, CURRENT_VERSION, guardianName, relation);
        if ("STUDENT".equals(role)) {
            consents.upsertConsent(userId, VOICE, Boolean.TRUE.equals(request.voice()), CURRENT_VERSION, guardianName, relation);
            consents.upsertConsent(userId, AI_CHAT, Boolean.TRUE.equals(request.aiChat()), CURRENT_VERSION, guardianName, relation);
        }
    }

    @Override
    public List<ConsentResponse> myConsents(TokenPrincipal principal) {
        Map<String,Object> profile = profile(principal);
        String role = String.valueOf(profile.get("role"));
        Integer age = profile.get("age") == null ? null : ((Number) profile.get("age")).intValue();
        Map<String, Map<String,Object>> rows = consents.findConsents(principal.userId()).stream()
                .collect(Collectors.toMap(row -> String.valueOf(row.get("type")), Function.identity()));
        return TYPES.stream()
                .filter(type -> applicable(type, role, age))
                .map(type -> toResponse(type, rows.get(type), required(type, role, age)))
                .toList();
    }

    @Override
    @Transactional
    public ConsentResponse updateConsent(TokenPrincipal principal, String rawType, ConsentUpdateRequest request) {
        Map<String,Object> profile = profile(principal);
        String role = String.valueOf(profile.get("role"));
        Integer age = profile.get("age") == null ? null : ((Number) profile.get("age")).intValue();
        String type = rawType == null ? "" : rawType.toUpperCase(java.util.Locale.ROOT);
        if (!TYPES.contains(type) || !applicable(type, role, age)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "변경할 수 없는 동의 항목입니다.");
        if (!CURRENT_VERSION.equals(request.policyVersion()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "동의 안내문이 갱신되었습니다. 새로고침 후 다시 시도해 주세요.");
        boolean agreed = request.agreed();
        if (!agreed && required(type, role, age))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "필수 동의는 화면에서 철회할 수 없어요. 철회(회원 탈퇴)는 소속 센터에 요청해 주세요.");
        String guardianName = null, relation = null;
        if (agreed && guardianRequired(role, age)) {
            requireGuardian(request.guardianConfirmed(), request.guardianName(), request.guardianRelation());
            guardianName = request.guardianName().trim(); relation = request.guardianRelation().trim();
        }
        consents.upsertConsent(principal.userId(), type, agreed, CURRENT_VERSION, guardianName, relation);
        // 음성 동의를 철회하면 보관 중인 녹음 파일을 즉시 삭제한다. 인식 텍스트·검토 기록은 학습 이력으로 남는다.
        if (!agreed && VOICE.equals(type) && profile.get("studentId") instanceof Number studentId)
            retention.deleteAllForStudent(studentId.longValue());
        return toResponse(type, consents.findConsent(principal.userId(), type), required(type, role, age));
    }

    @Override
    public void requireConsent(long userId, String type) {
        Map<String,Object> row = consents.findConsent(userId, type);
        if (row == null || !truthy(row.get("agreed"))) {
            String message = switch (type) {
                case VOICE -> "음성 녹음과 음성 인식을 이용하려면 마이페이지에서 '아동 음성 수집·이용'에 동의해 주세요.";
                case AI_CHAT -> "AI 대화를 이용하려면 마이페이지에서 'AI 대화 외부 전송'에 동의해 주세요.";
                default -> "필요한 동의가 없어 이용할 수 없습니다.";
            };
            throw new ConsentRequiredException(type, message);
        }
    }

    private void requireGuardian(Boolean confirmed, String name, String relation) {
        if (!Boolean.TRUE.equals(confirmed) || name == null || name.isBlank() || relation == null || relation.isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "만 14세 미만 학생은 법정대리인(보호자)의 이름·관계 입력과 동의 확인이 필요합니다.");
    }

    private Map<String,Object> profile(TokenPrincipal principal) {
        Map<String,Object> profile = principal == null ? null : consents.findUserProfile(principal.userId());
        if (profile == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "계정을 찾을 수 없습니다.");
        return profile;
    }

    private boolean applicable(String type, String role, Integer age) {
        if (PRIVACY.equals(type)) return true;
        if (GUARDIAN.equals(type)) return guardianRequired(role, age);
        return "STUDENT".equals(role);
    }

    private boolean required(String type, String role, Integer age) {
        return PRIVACY.equals(type) || (GUARDIAN.equals(type) && guardianRequired(role, age));
    }

    private ConsentResponse toResponse(String type, Map<String,Object> row, boolean required) {
        if (row == null) return new ConsentResponse(type, false, required, null, null, null, null, null, false);
        String version = (String) row.get("policyVersion");
        return new ConsentResponse(type, truthy(row.get("agreed")), required, version, (String) row.get("agreedAt"), (String) row.get("withdrawnAt"),
                (String) row.get("guardianName"), (String) row.get("guardianRelation"), CURRENT_VERSION.equals(version));
    }

    private boolean truthy(Object value) {
        return value instanceof Boolean flag ? flag : value instanceof Number number && number.intValue() != 0;
    }
}

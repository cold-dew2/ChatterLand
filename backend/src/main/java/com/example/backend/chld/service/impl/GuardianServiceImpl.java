package com.example.backend.chld.service.impl;

import com.example.backend.chld.dto.request.GuardianLinkRequest;
import com.example.backend.chld.dto.response.PageResponse;
import com.example.backend.chld.mapper.GuardianMapper;
import com.example.backend.chld.mapper.StudentMapper;
import com.example.backend.chld.mapper.UserMapper;
import com.example.backend.chld.service.GuardianService;
import com.example.backend.chld.service.LearningReportService;
import com.example.backend.global.jwt.TokenPrincipal;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class GuardianServiceImpl implements GuardianService {
    static final int CODE_MINUTES = 10;
    static final int CODE_LENGTH = 8;
    static final int MAX_FAILURES = 5;
    static final int FAILURE_WINDOW_MINUTES = 10;
    static final int MAX_CHILDREN = 10;
    /** 헷갈리는 글자(0/O, 1/I/L)를 뺀 31자 */
    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String NOT_LINKED = "연결된 자녀 정보를 찾을 수 없습니다.";

    private final GuardianMapper guardians;
    private final UserMapper users;
    private final StudentMapper students;
    private final LearningReportService learningReports;
    private final TransactionTemplate transaction;

    public GuardianServiceImpl(GuardianMapper guardians, UserMapper users, StudentMapper students, LearningReportService learningReports,
                               PlatformTransactionManager transactionManager) {
        this.guardians = guardians; this.users = users; this.students = students; this.learningReports = learningReports;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    // ── 자녀(학생) 계정 ─────────────────────────────────────────────

    /** 새 코드를 만들면 이전 미사용 코드는 만료된다. 코드 원문은 응답에서만 한 번 보여 주고 해시만 저장한다. */
    @Override @Transactional
    public Map<String,Object> createLinkCode(TokenPrincipal principal) {
        long studentId = studentId(principal);
        guardians.expireOpenCodes(studentId);
        for (int attempt = 0; attempt < 5; attempt++) {
            String code = newCode();
            try {
                guardians.insertLinkCode(studentId, hash(code), CODE_MINUTES);
                Map<String,Object> result = new LinkedHashMap<>();
                result.put("code", code.substring(0, 4) + "-" + code.substring(4));
                result.put("expiresAt", guardians.findCodeExpiry(hash(code)));
                result.put("validMinutes", CODE_MINUTES);
                return result;
            } catch (DuplicateKeyException collision) {
                // 아주 드문 충돌: 다른 코드로 다시
            }
        }
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "연결 코드를 만들지 못했어요. 잠시 후 다시 시도해 주세요.");
    }

    @Override
    public List<Map<String,Object>> myGuardians(TokenPrincipal principal) {
        return guardians.findActiveGuardians(studentId(principal));
    }

    @Override
    public void unlinkGuardian(TokenPrincipal principal, long guardianId) {
        if (guardians.unlink(guardianId, studentId(principal), "STUDENT") != 1)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "연결된 보호자를 찾을 수 없습니다.");
    }

    // ── 보호자 계정 ─────────────────────────────────────────────

    @Override
    public List<Map<String,Object>> children(TokenPrincipal principal) {
        return guardians.findActiveChildren(requireGuardian(principal));
    }

    /**
     * 코드가 맞으면 자녀와 연결한다(코드는 1회용). 틀린 코드는 실패 기록을 남기고, 10분에 5회를 넘으면 잠시 막는다.
     * 실패 기록은 요청이 실패해도 남아야 하므로 이 메서드 전체를 하나의 트랜잭션으로 묶지 않는다.
     */
    @Override
    public Map<String,Object> linkChild(TokenPrincipal principal, GuardianLinkRequest request) {
        long guardianId = requireGuardian(principal);
        if (guardians.countRecentLinkFailures(guardianId, FAILURE_WINDOW_MINUTES) >= MAX_FAILURES)
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "연결 코드를 여러 번 잘못 입력했어요. 10분 뒤에 다시 시도해 주세요.");
        String code = normalize(request.code());
        Long studentId = code.length() != CODE_LENGTH ? null : transaction.execute(status -> {
            Long found = guardians.findStudentByOpenCode(hash(code));
            if (found == null) return null;
            if (guardians.countActiveChildren(guardianId) >= MAX_CHILDREN && guardians.findActiveChild(guardianId, found) == null)
                throw new ResponseStatusException(HttpStatus.CONFLICT, "자녀는 " + MAX_CHILDREN + "명까지 연결할 수 있어요.");
            guardians.markCodeUsed(hash(code), guardianId);
            guardians.upsertLink(guardianId, found);
            return found;
        });
        if (studentId == null) {
            guardians.insertLinkFailure(guardianId);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "연결 코드가 맞지 않거나 시간이 지났어요. 자녀 계정에서 새 코드를 만들어 주세요.");
        }
        return guardians.findActiveChild(guardianId, studentId);
    }

    /** 연결된 자녀의 기본 정보·연결 상태·동의 상태(보기만). 자녀 계정의 동의는 보호자가 바꿀 수 없다. */
    @Override
    public Map<String,Object> child(TokenPrincipal principal, long studentId) {
        Map<String,Object> child = new LinkedHashMap<>(activeChild(requireGuardian(principal), studentId));
        child.put("consents", guardians.findChildConsents(studentId));
        return child;
    }

    @Override
    public void unlinkChild(TokenPrincipal principal, long studentId) {
        if (guardians.unlink(requireGuardian(principal), studentId, "GUARDIAN") != 1)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_LINKED);
    }

    /** 학생 본인·담당 선생님 화면과 같은 집계 */
    @Override
    public Map<String,Object> childReport(TokenPrincipal principal, long studentId, String period) {
        activeChild(requireGuardian(principal), studentId);
        return learningReports.report(studentId, period);
    }

    @Override
    public PageResponse<Map<String,Object>> childHistory(TokenPrincipal principal, long studentId, int page, int size) {
        activeChild(requireGuardian(principal), studentId);
        PageResponse.Window window = PageResponse.window(page, size);
        return PageResponse.of(students.findHistory(studentId, null, window.size(), window.offset()), students.countHistory(studentId, null), window);
    }

    // ── 공통 ─────────────────────────────────────────────

    private Map<String,Object> activeChild(long guardianId, long studentId) {
        Map<String,Object> child = guardians.findActiveChild(guardianId, studentId);
        if (child == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_LINKED);
        return child;
    }

    private static long requireGuardian(TokenPrincipal principal) {
        if (principal == null || !"GUARDIAN".equals(principal.role())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "보호자 권한이 필요합니다.");
        return principal.userId();
    }

    private long studentId(TokenPrincipal principal) {
        if (principal == null || !"STUDENT".equals(principal.role())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "학생 권한이 필요합니다.");
        Long id = users.findStudentIdByUserId(principal.userId());
        if (id == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "학생 프로필을 찾을 수 없습니다.");
        return id;
    }

    static String newCode() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        return code.toString();
    }

    /** 사용자가 넣은 코드에서 하이픈·공백을 빼고 대문자로 */
    static String normalize(String raw) {
        return raw == null ? "" : raw.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
    }

    static String hash(String code) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(code.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static Set<Character> alphabet() { return Set.copyOf(ALPHABET.chars().mapToObj(c -> (char) c).toList()); }
}

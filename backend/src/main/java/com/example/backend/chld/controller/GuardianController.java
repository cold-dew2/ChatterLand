package com.example.backend.chld.controller;

import com.example.backend.chld.dto.request.GuardianLinkRequest;
import com.example.backend.chld.dto.response.PageResponse;
import com.example.backend.chld.service.GuardianService;
import com.example.backend.global.jwt.TokenPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 보호자↔자녀 계정 연결. 자녀(학생) 쪽: 연결 코드 발급·연결된 보호자 보기·해제. 보호자 쪽: 자녀 연결·목록·상세·리포트·기록·해제.
 * 역할과 연결 여부는 서비스에서 로그인 정보로 확인한다(경로의 학생 ID는 연결 확인에만 쓴다).
 */
@RestController
@RequestMapping("/api/v1")
public class GuardianController {
    private final GuardianService guardians;

    public GuardianController(GuardianService guardians) { this.guardians = guardians; }

    @PostMapping("/students/me/guardian-link-codes") @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> createLinkCode(@AuthenticationPrincipal TokenPrincipal principal) { return guardians.createLinkCode(principal); }

    @GetMapping("/students/me/guardians")
    public List<Map<String,Object>> myGuardians(@AuthenticationPrincipal TokenPrincipal principal) { return guardians.myGuardians(principal); }

    @GetMapping("/students/me/guardian-events")
    public List<Map<String,Object>> linkEvents(@AuthenticationPrincipal TokenPrincipal principal) { return guardians.linkEvents(principal); }

    @DeleteMapping("/students/me/guardians/{guardianId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlinkGuardian(@AuthenticationPrincipal TokenPrincipal principal, @PathVariable long guardianId) { guardians.unlinkGuardian(principal, guardianId); }

    @GetMapping("/guardians/me/children")
    public List<Map<String,Object>> children(@AuthenticationPrincipal TokenPrincipal principal) { return guardians.children(principal); }

    @PostMapping("/guardians/me/children") @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> linkChild(@AuthenticationPrincipal TokenPrincipal principal, @Valid @RequestBody GuardianLinkRequest request) {
        return guardians.linkChild(principal, request);
    }

    @GetMapping("/guardians/me/children/{studentId}")
    public Map<String,Object> child(@AuthenticationPrincipal TokenPrincipal principal, @PathVariable long studentId) { return guardians.child(principal, studentId); }

    @DeleteMapping("/guardians/me/children/{studentId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlinkChild(@AuthenticationPrincipal TokenPrincipal principal, @PathVariable long studentId) { guardians.unlinkChild(principal, studentId); }

    @GetMapping("/guardians/me/children/{studentId}/learning-report")
    public Map<String,Object> childReport(@AuthenticationPrincipal TokenPrincipal principal, @PathVariable long studentId,
                                          @RequestParam(required = false) String period) { return guardians.childReport(principal, studentId, period); }

    @GetMapping("/guardians/me/children/{studentId}/history")
    public PageResponse<Map<String,Object>> childHistory(@AuthenticationPrincipal TokenPrincipal principal, @PathVariable long studentId,
                                                         @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return guardians.childHistory(principal, studentId, page, size);
    }
}

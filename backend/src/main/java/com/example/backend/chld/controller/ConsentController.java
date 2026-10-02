package com.example.backend.chld.controller;

import com.example.backend.chld.dto.request.ConsentUpdateRequest;
import com.example.backend.chld.dto.response.ConsentResponse;
import com.example.backend.chld.service.ConsentService;
import com.example.backend.global.jwt.TokenPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 본인 동의 내역 조회·변경. 다른 사용자(선생님 포함)의 동의 기록은 조회할 수 없다. */
@RestController
@RequestMapping("/api/v1/consents/me")
public class ConsentController {
    private final ConsentService consents;

    public ConsentController(ConsentService consents) { this.consents = consents; }

    @GetMapping public List<ConsentResponse> myConsents(@AuthenticationPrincipal TokenPrincipal principal) { return consents.myConsents(principal); }

    @PutMapping("/{type}") public ConsentResponse update(@AuthenticationPrincipal TokenPrincipal principal, @PathVariable String type,
                                                          @Valid @RequestBody ConsentUpdateRequest request) {
        return consents.updateConsent(principal, type, request);
    }
}

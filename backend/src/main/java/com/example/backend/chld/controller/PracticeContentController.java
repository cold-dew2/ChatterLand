package com.example.backend.chld.controller;

import com.example.backend.chld.dto.response.PageResponse;
import com.example.backend.chld.service.PracticeContentService;
import com.example.backend.global.jwt.TokenPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 연습 콘텐츠 검색(학생 자율 연습·교사 숙제 만들기). 기존 /practice/{categoryId}/exercises는 그대로 둔다. */
@RestController
@RequestMapping("/api/v1/practice-contents")
public class PracticeContentController {
    private final PracticeContentService contents;

    public PracticeContentController(PracticeContentService contents) { this.contents = contents; }

    @GetMapping
    public PageResponse<Map<String,Object>> search(@AuthenticationPrincipal TokenPrincipal principal,
            @RequestParam(required = false) String keyword, @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) String difficulty, @RequestParam(required = false) String rule,
            @RequestParam(required = false) String contentType,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return contents.search(principal, keyword, categoryId, difficulty, rule, contentType, page, size);
    }

    @GetMapping("/{exerciseId}")
    public Map<String,Object> detail(@AuthenticationPrincipal TokenPrincipal principal, @PathVariable long exerciseId) {
        return contents.detail(principal, exerciseId);
    }
}

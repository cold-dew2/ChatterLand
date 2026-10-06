package com.example.backend.chld.service.impl;

import com.example.backend.chld.dto.response.PageResponse;
import com.example.backend.chld.mapper.PracticeContentMapper;
import com.example.backend.chld.service.PracticeContentService;
import com.example.backend.global.jwt.TokenPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class PracticeContentServiceImpl implements PracticeContentService {
    static final Set<String> DIFFICULTIES = Set.of("BEGINNER", "INTERMEDIATE", "ADVANCED");
    static final Set<String> CONTENT_TYPES = Set.of("WORD", "SHORT_SENTENCE", "LONG_SENTENCE");
    static final Set<String> RULES = Set.of("BASIC_CONSONANT", "BASIC_VOWEL", "CODA", "LIAISON", "NASALIZATION", "TENSIFICATION",
            "PALATALIZATION", "ASPIRATION", "CONSONANT_ASSIMILATION", "COMPREHENSIVE");

    private final PracticeContentMapper mapper;

    public PracticeContentServiceImpl(PracticeContentMapper mapper) { this.mapper = mapper; }

    @Override
    public PageResponse<Map<String,Object>> search(TokenPrincipal principal, String keyword, String categoryId, String difficulty,
                                                   String rule, String contentType, int page, int size) {
        requireUser(principal);
        String k = keyword == null || keyword.isBlank() ? null : keyword.trim();
        if (k != null && k.length() > 50) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "검색어는 50자 이하로 입력해 주세요.");
        String c = blankToNull(categoryId), d = checked(difficulty, DIFFICULTIES, "난이도"), r = checked(rule, RULES, "발음 유형"),
                t = checked(contentType, CONTENT_TYPES, "콘텐츠 유형");
        PageResponse.Window window = PageResponse.window(page, Math.min(size, 50));
        List<Map<String,Object>> rows = mapper.searchExercises(k, c, d, r, t, window.size(), window.offset());
        attachItems(rows);
        return PageResponse.of(rows, mapper.countExercises(k, c, d, r, t), window);
    }

    @Override
    public Map<String,Object> detail(TokenPrincipal principal, long exerciseId) {
        requireUser(principal);
        Map<String,Object> exercise = mapper.findActiveExercise(exerciseId);
        if (exercise == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "연습 콘텐츠를 찾을 수 없습니다.");
        List<Map<String,Object>> rows = new ArrayList<>(List.of(new LinkedHashMap<>(exercise)));
        attachItems(rows);
        return rows.get(0);
    }

    private void attachItems(List<Map<String,Object>> exercises) {
        if (exercises.isEmpty()) return;
        List<Long> ids = exercises.stream().map(e -> ((Number) e.get("exerciseId")).longValue()).toList();
        Map<Long, List<Map<String,Object>>> byExercise = new LinkedHashMap<>();
        for (Map<String,Object> item : mapper.findItems(ids))
            byExercise.computeIfAbsent(((Number) item.get("exerciseId")).longValue(), key -> new ArrayList<>()).add(item);
        for (Map<String,Object> exercise : exercises)
            exercise.put("items", byExercise.getOrDefault(((Number) exercise.get("exerciseId")).longValue(), List.of()));
    }

    private static void requireUser(TokenPrincipal principal) {
        if (principal == null || !("STUDENT".equals(principal.role()) || "TEACHER".equals(principal.role())))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "연습 콘텐츠를 볼 수 없는 계정입니다.");
    }

    private static String checked(String value, Set<String> allowed, String label) {
        String v = blankToNull(value);
        if (v != null && !allowed.contains(v)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + " 값을 확인해 주세요.");
        return v;
    }

    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}

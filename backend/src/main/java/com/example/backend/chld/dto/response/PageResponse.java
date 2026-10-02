package com.example.backend.chld.dto.response;

import java.util.List;

public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages, boolean first, boolean last) {

    public static Window window(int page, int size) {
        int safeSize = Math.max(1, Math.min(100, size));
        int safePage = Math.max(0, page);
        return new Window(safePage, safeSize, safePage * safeSize);
    }

    public static <T> PageResponse<T> of(List<T> rows, long total, Window window) {
        int totalPages = (int) Math.ceil((double) total / window.size());
        return new PageResponse<>(rows, window.page(), window.size(), total, totalPages,
                window.page() == 0, window.page() + 1 >= totalPages);
    }

    public record Window(int page, int size, int offset) { }
}

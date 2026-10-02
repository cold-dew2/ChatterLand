package com.example.backend.chld.dto.response;

import java.math.BigDecimal;

public record SpeechProviderResponse(
        BigDecimal pronunciationScore,
        BigDecimal speechRateScore,
        BigDecimal fluencyScore,
        BigDecimal overallScore,
        String transcript,
        String feedback
) { }

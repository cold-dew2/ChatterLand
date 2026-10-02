package com.example.backend.chld.service;

import com.example.backend.chld.dto.response.SpeechRecognitionResult;

import java.nio.file.Path;

/**
 * 음성 인식(ASR) 엔진 추상화. 모델 교체 시 구현체만 바꾼다.
 * 인식 결과는 텍스트이며 발음 평가 결과로 취급하지 않는다.
 */
public interface SpeechRecognitionService {
    /** 엔진과 모델을 사용할 수 없으면 503 예외를 던진다. */
    void requireAvailable();

    SpeechRecognitionResult recognize(Path audioPath);
}

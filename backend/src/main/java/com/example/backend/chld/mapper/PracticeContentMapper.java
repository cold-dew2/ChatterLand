package com.example.backend.chld.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/** 연습 콘텐츠(연습 세트 exercises + 문항 exercise_items) 검색. 학생 자율 연습과 교사 숙제 만들기가 함께 쓴다. */
@Mapper
public interface PracticeContentMapper {
    List<Map<String,Object>> searchExercises(@Param("keyword") String keyword, @Param("categoryId") String categoryId,
                                             @Param("difficulty") String difficulty, @Param("rule") String rule,
                                             @Param("contentType") String contentType, @Param("limit") int limit, @Param("offset") int offset);
    long countExercises(@Param("keyword") String keyword, @Param("categoryId") String categoryId, @Param("difficulty") String difficulty,
                        @Param("rule") String rule, @Param("contentType") String contentType);
    Map<String,Object> findActiveExercise(@Param("exerciseId") long exerciseId);
    /** 여러 연습 세트의 문항을 한 번에(목록 N+1 방지) */
    List<Map<String,Object>> findItems(@Param("exerciseIds") List<Long> exerciseIds);
}

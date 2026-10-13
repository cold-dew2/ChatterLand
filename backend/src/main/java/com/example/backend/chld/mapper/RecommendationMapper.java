package com.example.backend.chld.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/** 규칙형 학습 추천에 쓰는 조회(저장된 연습 기록·활성 콘텐츠·진행 중 숙제만 본다). */
@Mapper
public interface RecommendationMapper {
    /** 활성 영역과 영역별 전체 연습 횟수(기록이 없으면 0) */
    List<Map<String,Object>> findAreas(@Param("studentId") long studentId);
    /** 가장 최근 연습 기록의 콘텐츠(영역·순서). 없으면 null */
    Map<String,Object> findLastPracticed(@Param("studentId") long studentId);
    /** 아직 끝나지 않은 숙제에 들어 있는 콘텐츠 ID */
    List<Long> findPendingHomeworkExerciseIds(@Param("studentId") long studentId);
    long countPendingHomeworks(@Param("studentId") long studentId);
    /**
     * 영역에서 이 학생이 아직 연습하지 않은 활성 콘텐츠 중 첫 번째(정렬 순서).
     * afterSortOrder가 있으면 그 콘텐츠 다음부터 찾는다. exclude의 콘텐츠는 건너뛴다.
     */
    Map<String,Object> findFirstUnpracticed(@Param("studentId") long studentId, @Param("categoryId") String categoryId,
            @Param("afterSortOrder") Integer afterSortOrder, @Param("afterExerciseId") Long afterExerciseId,
            @Param("exclude") Collection<Long> exclude);
    /**
     * 제목이나 문항에 낱말이 들어 있는 활성 콘텐츠(이 학생이 아직 연습하지 않은 것 먼저). matched: 낱말이 들어 있는 문항 하나
     */
    List<Map<String,Object>> findContentsMentioning(@Param("studentId") long studentId, @Param("keyword") String keyword,
            @Param("exclude") Collection<Long> exclude, @Param("limit") int limit);
}

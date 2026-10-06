package com.example.backend.chld.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/** RAG 지식베이스(한국어 발음 교육 자료) 조회 */
@Mapper
public interface KnowledgeMapper {
    /** 검색 대상 청크와 문서 메타데이터. 출처·사용 권한 확인(VERIFIED) + statuses에 든 검수 상태(기본 APPROVED)의 문서만. 교사 설명 예시는 승인자 필수. */
    List<Map<String,Object>> findSearchableChunks(@Param("statuses") List<String> statuses);
}

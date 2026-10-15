package com.example.backend.chld.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/** RAG 지식베이스(한국어 발음 교육 자료) 조회 */
@Mapper
public interface KnowledgeMapper {
    /**
     * 검색 대상 청크와 문서 메타데이터. 출처·사용 권한 확인(VERIFIED) + 내용 승인(APPROVED 고정) + 승인 기록(승인자 계정·승인 시각)이 있는 문서만.
     * 교사 설명 예시는 승인자가 TEACHER 계정이어야 한다. statuses는 좁히기만 하며(APPROVED와 AND) 비어 있으면 결과가 없다.
     */
    List<Map<String,Object>> findSearchableChunks(@Param("statuses") List<String> statuses);
}

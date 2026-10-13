package com.example.backend.chld.service;

import com.example.backend.chld.dto.request.GuardianLinkRequest;
import com.example.backend.chld.dto.response.PageResponse;
import com.example.backend.global.jwt.TokenPrincipal;

import java.util.List;
import java.util.Map;

/**
 * 보호자 계정과 자녀(학생) 계정 연결.
 * - 연결: 자녀 계정에서 발급한 1회용 코드(10분)를 보호자가 입력해야 한다. 학생 ID는 클라이언트에서 받지 않는다.
 * - 조회: 보호자는 ACTIVE로 연결된 자녀만 볼 수 있다(아니면 404 — 존재 여부도 알리지 않음). 읽기 전용.
 * - 해제: 보호자·자녀 어느 쪽에서도 할 수 있고, 해제 즉시 조회가 막힌다.
 * 이 연결은 법정대리인 동의 기록과 별개이며, 보호자 본인 확인을 대신하지 않는다.
 */
public interface GuardianService {
    // 자녀(학생) 계정
    Map<String,Object> createLinkCode(TokenPrincipal student);
    List<Map<String,Object>> myGuardians(TokenPrincipal student);
    void unlinkGuardian(TokenPrincipal student, long guardianId);

    // 보호자 계정
    List<Map<String,Object>> children(TokenPrincipal guardian);
    Map<String,Object> linkChild(TokenPrincipal guardian, GuardianLinkRequest request);
    Map<String,Object> child(TokenPrincipal guardian, long studentId);
    void unlinkChild(TokenPrincipal guardian, long studentId);
    Map<String,Object> childReport(TokenPrincipal guardian, long studentId, String period);
    PageResponse<Map<String,Object>> childHistory(TokenPrincipal guardian, long studentId, int page, int size);
}

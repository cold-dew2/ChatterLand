// 회원가입 결과를 반환하는 DTO

// 1. package
package com.example.backend.member.dto;

// 2. import


// 3. 클래스 어노테이션
// @Builder
// record

// 4. 클래스 선언
public record SignupResponse(

        // 5. 응답 필드
        // success
        // user

) {

    // 6. 내부 DTO
    // UserResponse

}

/**
 * 회원가입 응답 처리 흐름
 *
 * 회원가입 요청
 * ↓
 * UserService 실행
 * ↓
 * 회원 저장
 * ↓
 * SignupResponse 생성
 * ↓
 * Controller 반환
 * ↓
 * JSON 응답
 * ↓
 * 프론트 전달
 */
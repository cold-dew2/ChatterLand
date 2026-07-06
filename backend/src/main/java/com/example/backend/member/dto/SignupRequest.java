// 회원가입 요청 데이터를 담는 DTO

// 1. package
package com.example.backend.member.dto;

// 2. import


// 3. 클래스 어노테이션
// @Getter

// 4. 클래스 선언
public class SignupRequest {

    // 5. 검증 어노테이션
    // @NotBlank
    // @Email

    // 6. 요청 데이터 필드
    // userId
    // password
    // email
    // name
    // birthDate

    // 7. Getter
}

/**
 * SignupRequest 역할
 *
 * 프론트가 보낸 회원가입 데이터를 담는다.
 *
 * 예시
 *
 * {
 *   "userId": "tester",
 *   "password": "1234",
 *   "email": "test@test.com",
 *   "name": "홍길동",
 *   "birthDate": "2000-01-01"
 * }
 *
 * 처리 순서
 *
 * JSON 요청
 * ↓
 * SignupRequest 생성
 * ↓
 * Controller 전달
 * ↓
 * Service 전달
 */
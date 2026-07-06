// 회원 정보를 저장하는 엔티티

// 1. package
package com.example.backend.member.entity;

// 2. import


// 3. 클래스 어노테이션
// @Entity
// @Table
// @Getter
// @NoArgsConstructor

// 4. 클래스 선언
public class User {

    // 5. 기본키(PK)
    // id

    // 6. 회원 정보 필드
    // userId
    // passwordHash
    // email
    // name
    // role
    // birthDate
    // createdAt

    // 7. 기본 생성자
    // JPA 사용

    // 8. 회원 생성자
}

/**
 * 회원 생성 처리 흐름
 *
 * 회원가입 요청
 * ↓
 * SignupRequest 생성
 * ↓
 * UserService 실행
 * ↓
 * User 엔티티 생성
 * ↓
 * UserRepository 저장
 * ↓
 * users 테이블 저장
 */
// User 엔티티의 DB 접근 담당

// 1. package
package com.example.backend.member.repository;

// 2. import


// 3. 인터페이스 선언
// JpaRepository 상속

public interface UserRepository {

    // 4. 기본 CRUD
    // save()
    // findById()
    // delete()
    // findAll()

    // 5. 사용자 정의 조회 메서드
    // findByUserId()
    // findByEmail()

    // 6. 중복 확인 메서드
    // existsByUserId()
    // existsByEmail()
}

/**
 * UserRepository 역할
 *
 * User 엔티티의 DB 작업을 담당한다.
 *
 * 사용 예시
 *
 * 회원 저장
 * ↓
 * save(user)
 *
 * 회원 조회
 * ↓
 * findByUserId()
 *
 * 이메일 조회
 * ↓
 * findByEmail()
 *
 * 아이디 중복 확인
 * ↓
 * existsByUserId()
 *
 * 이메일 중복 확인
 * ↓
 * existsByEmail()
 *
 * 처리 순서
 *
 * Controller
 * ↓
 * Service
 * ↓
 * UserRepository
 * ↓
 * Database
 */
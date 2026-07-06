// 회원 관련 비즈니스 로직 처리

// 1. package
package com.example.backend.member.service;

// 2. import


// 3. 클래스 어노테이션
// @Service
// @RequiredArgsConstructor

// 4. 클래스 선언
public class UserService {

    // 5. Repository 주입
    // UserRepository

    // 6. Bean 주입
    // PasswordEncoder

    // 7. 회원가입 메서드
    // signup()

    // 8. private 메서드 (있다면)
}

/**
 * UserService 역할 : 회원 관련 비즈니스 로직을 처리한다.
 *
 * 사용 예시
 *
 * 회원가입
 * ↓
 * signup()
 *
 * 처리 순서
 *
 * Controller
 * ↓
 * signup() 호출
 * ↓
 * 아이디 중복 검사 existsByUserId()
 * ↓
 * 이메일 중복 검사 existsByEmail()
 * ↓
 * 비밀번호 암호화 passwordEncoder.encode()
 * ↓
 * User 엔티티 생성
 * ↓
 * DB 저장 userRepository.save()
 * ↓
 * SignupResponse 생성
 * ↓
 * Controller 반환
 */
// 프론트 요청을 받아 Service에게 전달하는 역할

// 1. package
package com.example.backend.member.controller;

// 2. import
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;

// 3. 클래스 어노테이션
@RestController
@RequestMapping("/api")
//   @RequiredArgsConstructor

// 4. 클래스 선언
public class UserController {

    @GetMapping("/hello")
    public String hello(){
        return "Spring boot OK";
    }
    // 5. Service 주입

    // 6. API 메서드들
    // - signup()
    // - login()
    // - findUser()
    // - updateUser()

    // 7. private 메서드 (있다면)
}

/**
 * 회원가입 처리 흐름
 *
 * 요청 받기
 * ↓
 * 요청 검증
 * ↓
 * Service 호출
 * ↓
 * 회원 저장
 * ↓
 * 응답 반환
 */
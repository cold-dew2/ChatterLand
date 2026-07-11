package com.example.backend.chld.service;

import com.example.backend.chld.dto.*;
import com.example.backend.chld.entity.User;
import com.example.backend.chld.entity.UserHist;
import com.example.backend.chld.entity.UserCenterList;
import com.example.backend.chld.repository.UserRepository;
import com.example.backend.chld.repository.UserHistRepository;
import com.example.backend.chld.repository.UserCenterListRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final UserHistRepository userHistRepository;
    private final UserCenterListRepository userCenterListRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.expiration}")
    private long expiration;

    public ExistsUserIdResponse existsUserId(ExistsUserIdRequest request) {
        //System.out.println("request : " + request.getUserId());

        boolean userCount = userRepository.existsByUserId(request.getUserId());

        if (userCount){
            return new ExistsUserIdResponse(
                    false,
                    409,
                    "USER_ALREADY_EXISTS",
                    "이미 존재하는 아이디",
                    "/login/existsUserId",
                    ""
            );
        }

        return new ExistsUserIdResponse(
                true,
                200,
                "SUCCESS",
                "사용가능한 아이디",
                "/login/existsUserId",
                ""
        );
    }

    public SignupResponse signup(SignupRequest request) {

        // 1. 아이디 중복 체크 (필수)
        if (userRepository.existsByUserId(request.getUserId())) {
            return new SignupResponse(
                    false,
                    409,
                    "USER_ALREADY_EXISTS",
                    "이미 존재하는 아이디",
                    "/login/signup",
                    ""
            );
        }

        // 2. Entity 생성 (INSERT 대상)
        User userInfo = new User();
        userInfo.setUserId(request.getUserId());
        userInfo.setUserPw(passwordEncoder.encode(request.getUserPw()));
        userInfo.setUserNm(request.getUserNm());
        userInfo.setEmail(request.getEmail());
        userInfo.setStateCd("A");
        userInfo.setRoleCd(request.getRoleCd());
        userInfo.setBirthDt(request.getBirthDt());
        userInfo.setGenderCd(request.getGenderCd());
        userInfo.setPhoneNum(request.getPhoneNum());

        userRepository.save(userInfo);

        UserCenterList ucInfo = new UserCenterList();
        ucInfo.setUserId(request.getUserId());
        ucInfo.setCenterId(request.getCenterId());
        ucInfo.setStateCd("A");

        userCenterListRepository.save(ucInfo);

        return new SignupResponse(
                true,
                200,
                "SUCCESS",
                "회원가입 성공",
                "/login/signup",
                ""
        );
    }

    public LoginResponse login(LoginRequest request) {
        Optional<User> optionalUser = userRepository.findByUserId(request.getUserId());
        if (optionalUser.isEmpty()) {
            return new LoginResponse(
                    false,
                    401,
                    "UNAUTHORIZED",
                    "아이디 또는 비밀번호가 올바르지 않습니다.",
                    "/login/login",
                    ""
            );
        }

        User user = optionalUser.get();
        if (!passwordEncoder.matches(request.getUserPw(), user.getUserPw())) {
            return new LoginResponse(
                    false,
                    401,
                    "UNAUTHORIZED",
                    "아이디 또는 비밀번호가 올바르지 않습니다.",
                    "/login/login",
                    ""
            );
        }

        String token = Jwts.builder()
                .subject(user.getUserId())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(
                        Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8)),
                        Jwts.SIG.HS256
                )
                .compact();


        UserHist userHist = new UserHist();
        userHist.setUserId(request.getUserId());
        userHistRepository.save(userHist);

        return new LoginResponse(
                true,
                200,
                "SUCCESS",
                "로그인 성공",
                "/login/login",
                token
        );
    }
}
package com.example.backend.chld.service;

import com.example.backend.chld.dto.request.ExistsUserIdRequest;
import com.example.backend.chld.dto.request.LoginRequest;
import com.example.backend.chld.dto.request.SignupRequest;
import com.example.backend.chld.dto.response.ExistsUserIdResponse;
import com.example.backend.chld.dto.response.LoginResponse;
import com.example.backend.chld.dto.response.SignupResponse;

public interface UserService {

    //아이디 중복확인
    ExistsUserIdResponse existsUserId(ExistsUserIdRequest request);

    //회원가입
    SignupResponse signup(SignupRequest request);

    //로그인
    LoginResponse login(LoginRequest request);

}
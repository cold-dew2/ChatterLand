package com.example.backend.chld.controller;

import com.example.backend.chld.dto.response.CenterResponse;
import com.example.backend.chld.service.CenterService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 회원가입·아이디 찾기에서 사용하는 공개 센터 목록 (사용 중인 센터만) */
@RestController
@RequestMapping("/api/v1/centers")
public class CenterController {
    private final CenterService centers;

    public CenterController(CenterService centers) { this.centers = centers; }

    @GetMapping public List<CenterResponse> centers() { return centers.activeCenters(); }
}

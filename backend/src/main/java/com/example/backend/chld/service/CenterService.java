package com.example.backend.chld.service;

import com.example.backend.chld.dto.response.CenterResponse;

import java.util.List;

public interface CenterService {
    List<CenterResponse> activeCenters();

    /** 존재하고 사용 중인 센터인지 확인한다. 아니면 400 예외를 던진다. */
    CenterResponse requireActiveCenter(Long centerId);
}

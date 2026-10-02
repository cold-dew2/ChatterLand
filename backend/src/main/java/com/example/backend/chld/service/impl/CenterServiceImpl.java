package com.example.backend.chld.service.impl;

import com.example.backend.chld.dto.response.CenterResponse;
import com.example.backend.chld.mapper.CenterMapper;
import com.example.backend.chld.service.CenterService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class CenterServiceImpl implements CenterService {
    private final CenterMapper centers;

    public CenterServiceImpl(CenterMapper centers) { this.centers = centers; }

    @Override public List<CenterResponse> activeCenters() { return centers.findActiveCenters(); }

    @Override public CenterResponse requireActiveCenter(Long centerId) {
        CenterResponse center = centerId == null ? null : centers.findActiveCenter(centerId);
        if (center == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "선택한 언어재활센터를 사용할 수 없습니다. 센터를 다시 선택해 주세요.");
        return center;
    }
}

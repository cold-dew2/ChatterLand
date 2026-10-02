package com.example.backend.chld.mapper;

import com.example.backend.chld.dto.response.CenterResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CenterMapper {
    List<CenterResponse> findActiveCenters();
    CenterResponse findActiveCenter(@Param("centerId") long centerId);
}

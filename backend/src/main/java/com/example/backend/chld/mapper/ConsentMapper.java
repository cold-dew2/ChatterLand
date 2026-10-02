package com.example.backend.chld.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface ConsentMapper {
    List<Map<String,Object>> findConsents(@Param("userId") long userId);
    Map<String,Object> findConsent(@Param("userId") long userId, @Param("type") String type);
    int upsertConsent(@Param("userId") long userId, @Param("type") String type, @Param("agreed") boolean agreed,
                      @Param("policyVersion") String policyVersion, @Param("guardianName") String guardianName,
                      @Param("guardianRelation") String guardianRelation);
    Map<String,Object> findUserProfile(@Param("userId") long userId);
}

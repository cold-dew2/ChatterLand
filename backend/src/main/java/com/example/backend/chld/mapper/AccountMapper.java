package com.example.backend.chld.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Map;

@Mapper
public interface AccountMapper {
    int updateUserName(@Param("userId") long userId, @Param("name") String name);
    int updateStudentProfileName(@Param("userId") long userId, @Param("name") String name);
    Map<String,Object> findNotificationSettings(@Param("userId") long userId);
    int upsertNotificationSettings(@Param("userId") long userId, @Param("homeworkAssigned") boolean homeworkAssigned,
            @Param("homeworkChanged") boolean homeworkChanged, @Param("homeworkDueSoon") boolean homeworkDueSoon, @Param("homeworkOverdue") boolean homeworkOverdue);
}

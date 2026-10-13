package com.example.backend.chld.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
/**
 * 부분 수정: 보내지 않은 값은 유지한다. 보낸 제목·유형은 생성(HomeworkCreateRequest)과 같이 공백만으로 둘 수 없다.
 * version(필수): 화면이 조회한 숙제 버전. 그 사이 다른 곳에서 바뀌었으면 409(VERSION_CONFLICT)로 거절한다. 없으면 400.
 */
public record HomeworkUpdateRequest(@Size(max=160) @Pattern(regexp="(?s).*\\S.*",message="숙제 제목을 입력해 주세요.") String title,
        @Size(max=60) @Pattern(regexp="(?s).*\\S.*",message="숙제 유형을 입력해 주세요.") String type,
        @Size(max=2000) String description,@Min(1) Integer targetMinutes,
        @FutureOrPresent LocalDate dueDate,String status,Boolean done,@NotNull(message="숙제 버전(version)이 필요합니다.") @Min(0) Integer version,
        @Size(max=30, message="숙제 콘텐츠는 30개까지 고를 수 있어요.") List<Long> exerciseIds) {
    /** exerciseIds(선택): 보내면 숙제의 연습 콘텐츠를 이 순서로 바꾼다(빈 목록이면 콘텐츠 없는 숙제). 보내지 않으면 그대로 둔다. 기존 호출 호환 생성자 */
    public HomeworkUpdateRequest(String title, String type, String description, Integer targetMinutes, LocalDate dueDate, String status, Boolean done, Integer version) {
        this(title, type, description, targetMinutes, dueDate, status, done, version, null);
    }
}

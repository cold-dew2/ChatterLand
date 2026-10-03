package com.example.backend.chld.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

@JsonIgnoreProperties(ignoreUnknown = true)
/**
 * 부분 수정: 보내지 않은 값은 유지한다. 보낸 제목·유형은 생성(HomeworkCreateRequest)과 같이 공백만으로 둘 수 없다.
 * version(필수): 화면이 조회한 숙제 버전. 그 사이 다른 곳에서 바뀌었으면 409(VERSION_CONFLICT)로 거절한다. 없으면 400.
 */
public record HomeworkUpdateRequest(@Size(max=160) @Pattern(regexp="(?s).*\\S.*",message="숙제 제목을 입력해 주세요.") String title,
        @Size(max=60) @Pattern(regexp="(?s).*\\S.*",message="숙제 유형을 입력해 주세요.") String type,
        @Size(max=2000) String description,@Min(1) Integer targetMinutes,
        @FutureOrPresent LocalDate dueDate,String status,Boolean done,@NotNull(message="숙제 버전(version)이 필요합니다.") @Min(0) Integer version) { }

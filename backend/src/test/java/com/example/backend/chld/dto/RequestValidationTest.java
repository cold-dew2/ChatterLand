package com.example.backend.chld.dto;

import com.example.backend.chld.dto.request.AttemptRequest;
import com.example.backend.chld.dto.request.HomeworkCreateRequest;
import com.example.backend.chld.dto.request.HomeworkUpdateRequest;
import com.example.backend.chld.dto.request.SignupRequest;
import com.example.backend.chld.dto.request.SpeechReviewRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** 요청 DTO 검증 규칙(jakarta.validation)을 서버 없이 확인한다. */
class RequestValidationTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<String> invalidFields(Object request) {
        return validator.validate(request).stream().map(ConstraintViolation::getPropertyPath).map(Object::toString).collect(Collectors.toSet());
    }

    @Test
    void signupRequiresRoleNameEmailPasswordLengthAndCenter() {
        assertTrue(invalidFields(new SignupRequest("STUDENT", "학생", "kid@example.test", "Chatterland!234", 1L, true, 8, null)).isEmpty());
        Set<String> invalid = invalidFields(new SignupRequest("", "", "not-email", "short", null, null, null, null));
        assertEquals(Set.of("role", "name", "email", "password", "centerId", "termsAgreed"), invalid);
        assertTrue(invalidFields(new SignupRequest("STUDENT", "가".repeat(81), "kid@example.test", "x".repeat(73), 1L, true, 8, null))
                .containsAll(Set.of("name", "password")));
    }

    @Test
    void homeworkRejectsPastDueDateAndNonPositiveMinutes() {
        assertTrue(invalidFields(new HomeworkCreateRequest(1L, "숙제", "발음", LocalDate.now(), 10, null)).isEmpty());
        assertEquals(Set.of("dueDate", "targetMinutes"), invalidFields(new HomeworkCreateRequest(1L, "숙제", "발음", LocalDate.now().minusDays(1), 0, null)));
        assertTrue(invalidFields(new HomeworkCreateRequest(null, " ", "발음", LocalDate.now(), 10, "x".repeat(2001))).containsAll(Set.of("studentId", "title", "description")));
    }

    @Test
    void homeworkUpdateKeepsOmittedFieldsButRejectsBlankTitleAndType() {
        assertTrue(invalidFields(new HomeworkUpdateRequest(null, null, null, null, null, null, true, 0)).isEmpty(), "보내지 않은 값은 검증하지 않는다(부분 수정)");
        assertTrue(invalidFields(new HomeworkUpdateRequest(" 새 제목 ", "발음", "", 5, null, null, null, 0)).isEmpty());
        assertEquals(Set.of("title", "type"), invalidFields(new HomeworkUpdateRequest("   ", "\t\n", null, null, null, null, null, 0)));
        assertTrue(invalidFields(new HomeworkUpdateRequest("x".repeat(161), null, null, 0, LocalDate.now().minusDays(1), null, null, 0))
                .containsAll(Set.of("title", "targetMinutes", "dueDate")));
    }

    @Test
    void homeworkUpdateVersionIsRequiredAndCannotBeNegative() {
        assertTrue(invalidFields(new HomeworkUpdateRequest("제목", null, null, null, null, null, null, 0)).isEmpty());
        assertEquals(Set.of("version"), invalidFields(new HomeworkUpdateRequest("제목", null, null, null, null, null, null, -1)));
        assertEquals(Set.of("version"), invalidFields(new HomeworkUpdateRequest("제목", null, null, null, null, null, null, null)));
    }

    @Test
    void attemptScoreMustBeAMeasured0to100ValueWhenPresent() {
        assertTrue(invalidFields(new AttemptRequest("1", "11", null, "id")).isEmpty());
        assertEquals(Set.of("score"), invalidFields(new AttemptRequest("1", "11", 101.0, "id")));
        assertEquals(Set.of("score"), invalidFields(new AttemptRequest("1", "11", -1.0, "id")));
        assertEquals(Set.of("audioId"), invalidFields(new AttemptRequest("1", "11", null, " ")));
    }

    @Test
    void reviewAcceptsOnlyKnownJudgementsAndValidConfirmedErrors() {
        assertTrue(invalidFields(new SpeechReviewRequest("NEEDS_PRACTICE", null, null)).isEmpty());
        assertTrue(invalidFields(new SpeechReviewRequest("NEEDS_PRACTICE", "메모",
                List.of(new SpeechReviewRequest.ConfirmedError("ㄹ", "DISTORTION", null, "어두 초성")))).isEmpty());
        assertEquals(Set.of("judgement"), invalidFields(new SpeechReviewRequest("PERFECT", null, null)));
        Set<String> invalid = invalidFields(new SpeechReviewRequest("ACCEPTABLE", "x".repeat(1001),
                List.of(new SpeechReviewRequest.ConfirmedError("", "GUESS", "ㄷㄷㄷㄷㄷ", null))));
        assertTrue(invalid.containsAll(Set.of("note", "confirmedErrors[0].phoneme", "confirmedErrors[0].errorType", "confirmedErrors[0].produced")), invalid.toString());
        List<SpeechReviewRequest.ConfirmedError> tooMany = java.util.Collections.nCopies(21, new SpeechReviewRequest.ConfirmedError("ㄹ", "OMISSION", null, null));
        assertTrue(invalidFields(new SpeechReviewRequest("ACCEPTABLE", null, tooMany)).contains("confirmedErrors"));
    }
}

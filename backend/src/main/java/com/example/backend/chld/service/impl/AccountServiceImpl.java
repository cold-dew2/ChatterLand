package com.example.backend.chld.service.impl;

import com.example.backend.chld.dto.request.NotificationSettingsRequest;
import com.example.backend.chld.dto.request.ProfileUpdateRequest;
import com.example.backend.chld.dto.response.NotificationSettingsResponse;
import com.example.backend.chld.dto.response.UserResponse;
import com.example.backend.chld.mapper.AccountMapper;
import com.example.backend.chld.service.AccountService;
import com.example.backend.chld.service.AuthService;
import com.example.backend.global.jwt.TokenPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@Service
public class AccountServiceImpl implements AccountService {
    private final AccountMapper accounts;
    private final AuthService auth;

    public AccountServiceImpl(AccountMapper accounts, AuthService auth) { this.accounts = accounts; this.auth = auth; }

    /** 이름만 바꾼다. 학생은 학습 프로필 이름도 같이 바꿔 선생님 화면과 맞춘다(두 문장을 한 트랜잭션으로). */
    @Override @Transactional
    public UserResponse updateProfile(TokenPrincipal principal, ProfileUpdateRequest request) {
        long userId = requireUser(principal);
        String name = request.name().trim();
        if (accounts.updateUserName(userId, name) != 1) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "계정을 찾을 수 없습니다.");
        if ("STUDENT".equals(principal.role())) accounts.updateStudentProfileName(userId, name);
        return auth.currentUser(userId);
    }

    @Override
    public NotificationSettingsResponse notificationSettings(TokenPrincipal principal) {
        Map<String,Object> row = accounts.findNotificationSettings(requireUser(principal));
        if (row == null) return NotificationSettingsResponse.defaults();
        return new NotificationSettingsResponse(flag(row.get("homeworkAssigned")), flag(row.get("homeworkChanged")),
                flag(row.get("homeworkDueSoon")), flag(row.get("homeworkOverdue")));
    }

    @Override
    public NotificationSettingsResponse updateNotificationSettings(TokenPrincipal principal, NotificationSettingsRequest request) {
        long userId = requireUser(principal);
        accounts.upsertNotificationSettings(userId, request.homeworkAssigned(), request.homeworkChanged(), request.homeworkDueSoon(), request.homeworkOverdue());
        return notificationSettings(principal);
    }

    private static long requireUser(TokenPrincipal principal) {
        if (principal == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "인증이 필요합니다.");
        return principal.userId();
    }

    private static boolean flag(Object value) {
        return value instanceof Boolean b ? b : value instanceof Number n && n.intValue() != 0;
    }
}

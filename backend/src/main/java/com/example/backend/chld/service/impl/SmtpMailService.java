package com.example.backend.chld.service.impl;

import com.example.backend.chld.service.MailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * SMTP 메일 발송. MAIL_HOST가 없으면 JavaMailSender가 만들어지지 않으며, 이때 성공한 것처럼 처리하지 않고 503을 반환한다.
 * 인증 코드는 로그에 남기지 않는다.
 */
@Slf4j
@Service
public class SmtpMailService implements MailService {
    private final ObjectProvider<JavaMailSender> senderProvider;
    private final String from;

    public SmtpMailService(ObjectProvider<JavaMailSender> senderProvider, @Value("${app.mail.from:}") String from) {
        this.senderProvider = senderProvider; this.from = from;
    }

    @Override
    public void requireAvailable() {
        if (senderProvider.getIfAvailable() == null || from == null || from.isBlank())
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "메일 발송 설정(MAIL_HOST, MAIL_FROM)이 필요합니다. 센터 관리자에게 문의해 주세요.");
    }

    @Override
    public void sendPasswordResetCode(String to, String code, int expiresMinutes) {
        requireAvailable();
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject("[채터랜드] 비밀번호 재설정 인증 코드");
        message.setText("""
                채터랜드 비밀번호 재설정 인증 코드입니다.

                인증 코드: %s

                이 코드는 %d분 동안 한 번만 사용할 수 있습니다.
                본인이 요청하지 않았다면 이 메일을 무시해 주세요. 비밀번호는 변경되지 않습니다.
                """.formatted(code, expiresMinutes));
        try {
            senderProvider.getObject().send(message);
        } catch (MailException e) {
            log.warn("Password reset mail delivery failed: {}", e.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "인증 메일을 보내지 못했어요. 잠시 뒤 다시 시도해 주세요.");
        }
    }
}

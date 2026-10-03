package com.example.backend.chld.service.impl;

import com.example.backend.chld.exception.MailUnavailableException;
import com.example.backend.chld.service.MailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * SMTP 메일 발송. MAIL_HOST나 MAIL_FROM이 비어 있으면(서버는 정상 기동) 성공한 것처럼 처리하지 않고 503 MAIL_NOT_CONFIGURED를 반환한다.
 * MAIL_HOST가 빈 값이어도 Spring Boot는 JavaMailSender를 만들므로, 발송기 존재 여부가 아니라 설정값으로 판단한다.
 * SMTP 연결·발송 실패는 503 MAIL_DELIVERY_FAILED.
 * 인증 코드는 로그에 남기지 않는다.
 */
@Slf4j
@Service
public class SmtpMailService implements MailService {
    private final ObjectProvider<JavaMailSender> senderProvider;
    private final String from;
    private final String host;

    public SmtpMailService(ObjectProvider<JavaMailSender> senderProvider, @Value("${app.mail.from:}") String from,
                           @Value("${spring.mail.host:}") String host) {
        this.senderProvider = senderProvider; this.from = from; this.host = host;
    }

    @Override
    public void requireAvailable() {
        if (host == null || host.isBlank() || from == null || from.isBlank() || senderProvider.getIfAvailable() == null)
            throw new MailUnavailableException(MailUnavailableException.NOT_CONFIGURED, "메일 발송 설정(MAIL_HOST, MAIL_FROM)이 필요합니다. 센터 관리자에게 문의해 주세요.");
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
            throw new MailUnavailableException(MailUnavailableException.DELIVERY_FAILED, "인증 메일을 보내지 못했어요. 잠시 뒤 다시 시도해 주세요.");
        }
    }
}

package com.nguyenvu.lopet.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class ResendMailSender {

    private final Resend resend;
    private final String from;

    public ResendMailSender(Resend resend, @Value("${lopet.mail.from}") String from) {
        this.resend = resend;
        this.from = from;
    }

    public String sendHtml(String to, String subject, String html) throws ResendException {
        CreateEmailOptions options = CreateEmailOptions.builder()
                .from(from)
                .to(to)
                .subject(subject)
                .html(html)
                .build();

        CreateEmailResponse response = resend.emails().send(options);
        log.debug("Resend accepted mail {} addressed to {}", response.getId(), to);
        return response.getId();
    }
}

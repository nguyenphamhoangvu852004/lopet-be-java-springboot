package com.nguyenvu.lopet.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;

import lombok.extern.slf4j.Slf4j;

/**
 * Nơi duy nhất chạm vào SDK của Resend. Mọi chỗ cần gửi mail đi qua đây để địa chỉ người gửi và cách
 * xử lý lỗi chỉ có một bản.
 *
 * <p>{@code lopet.mail.from} phải là địa chỉ thuộc domain đã verify trong dashboard Resend. Domain
 * chưa verify thì API trả 403 chứ không phải lỗi gửi thư — khi test có thể dùng
 * {@code onboarding@resend.dev}, nhưng địa chỉ đó chỉ gửi được tới chính email chủ tài khoản Resend.
 */
@Slf4j
@Component
public class ResendMailSender {

    private final Resend resend;
    private final String from;

    public ResendMailSender(Resend resend, @Value("${lopet.mail.from}") String from) {
        this.resend = resend;
        this.from = from;
    }

    /**
     * Gửi một mail HTML. Resend nhận request là trả id ngay, việc giao thư diễn ra bất đồng bộ phía
     * họ — id trả về chỉ chứng minh mail đã được nhận vào hàng đợi, không chứng minh đã tới hòm thư.
     *
     * @return id của mail bên Resend, dùng để tra cứu trong dashboard
     */
    public String sendHtml(String to, String subject, String html) throws ResendException {
        CreateEmailOptions options = CreateEmailOptions.builder()
                .from(from)
                .to(to)
                .subject(subject)
                .html(html)
                .build();

        CreateEmailResponse response = resend.emails().send(options);
        log.debug("Resend đã nhận mail {} gửi tới {}", response.getId(), to);
        return response.getId();
    }
}

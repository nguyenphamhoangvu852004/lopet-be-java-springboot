package com.nguyenvu.lopet.email;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import com.nguyenvu.lopet.common.exception.BadRequestException;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EmailService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final JavaMailSender mailSender;
    private final OtpStore otpStore;

    /**
     * Còn OTP chưa hết hạn thì từ chối phát mã mới — đây là cơ chế chống spam gửi mail duy nhất của
     * hệ thống. Lỗi trả về dùng message mặc định "BAD REQUEST" đúng như bản TS ({@code new BadRequest()}).
     */
    public void sendOtp(String email) {
        if (otpStore.findOtp(email) != null) {
            throw new BadRequestException();
        }

        String otp = generateOtp();
        otpStore.saveOtp(email, otp);
        send(email, otp);
    }

    public void verify(String email, String otp) {
        String saved = otpStore.findOtp(email);
        if (saved == null) {
            throw new BadRequestException("OTP đã hết hạn hoặc không tồn tại.");
        }
        if (!saved.equals(otp)) {
            throw new BadRequestException("OTP không chính xác.");
        }

        otpStore.deleteOtp(email);
        otpStore.markVerified(email);
    }

    /** 6 chữ số, cùng dải 100000–999999 với {@code generateOTP()} bên TS */
    private String generateOtp() {
        return String.valueOf(100_000 + RANDOM.nextInt(900_000));
    }

    private void send(String email, String otp) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setTo(email);
            helper.setSubject("Mã xác thực OTP của bạn");
            helper.setText(body(otp), true);
            mailSender.send(message);
        } catch (Exception exception) {
            throw new BadRequestException("Send email failed");
        }
    }

    private String body(String otp) {
        return """
                <div style="font-family: Arial, sans-serif; max-width: 600px; margin: auto;">
                  <h2 style="color: #333;">Xác thực tài khoản</h2>
                  <p>Chào bạn,</p>
                  <p>Cảm ơn bạn đã đăng ký tài khoản. Đây là mã OTP để xác thực địa chỉ email của bạn:</p>
                  <p style="font-size: 24px; font-weight: bold; color:rgb(111, 0, 255);">%s</p>
                  <p>Mã OTP này có hiệu lực trong 2 phút.</p>
                  <p>Nếu bạn không yêu cầu mã OTP này, vui lòng bỏ qua email này.</p>
                  <br>
                  <p>Trân trọng,</p>
                  <p><strong>Đội ngũ hỗ trợ</strong></p>
                </div>
                """.formatted(otp);
    }
}

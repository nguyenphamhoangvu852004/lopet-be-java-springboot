package com.nguyenvu.lopet.email;

import java.security.SecureRandom;

import org.springframework.stereotype.Service;

import com.nguyenvu.lopet.common.exception.BadRequestException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ResendMailSender mailSender;
    private final OtpStore otpStore;

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
            throw new BadRequestException("The OTP has expired or does not exist.");
        }
        if (!saved.equals(otp)) {
            throw new BadRequestException("The OTP is incorrect.");
        }

        otpStore.deleteOtp(email);
        otpStore.markVerified(email);
    }

    private String generateOtp() {
        return String.valueOf(100_000 + RANDOM.nextInt(900_000));
    }

    private void send(String email, String otp) {
        try {
            mailSender.sendHtml(email, "Your OTP verification code", body(otp));
        } catch (Exception exception) {
            log.error("Failed to send the OTP mail to {}: {}", email, exception.getMessage());
            throw new BadRequestException("Send email failed");
        }
    }

    private String body(String otp) {
        return """
                <div style="font-family: Arial, sans-serif; max-width: 600px; margin: auto;">
                  <h2 style="color: #333;">Account verification</h2>
                  <p>Hello,</p>
                  <p>Thank you for signing up. Here is the OTP to verify your email address:</p>
                  <p style="font-size: 24px; font-weight: bold; color:rgb(111, 0, 255);">%s</p>
                  <p>This OTP is valid for 2 minutes.</p>
                  <p>If you did not request this OTP, please ignore this email.</p>
                  <br>
                  <p>Best regards,</p>
                  <p><strong>The support team</strong></p>
                </div>
                """.formatted(otp);
    }
}

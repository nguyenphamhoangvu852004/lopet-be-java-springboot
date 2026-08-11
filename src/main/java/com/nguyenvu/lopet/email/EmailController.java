package com.nguyenvu.lopet.email;

import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nguyenvu.lopet.common.response.ApiResponse;

import lombok.RequiredArgsConstructor;

/**
 * Hai endpoint này không có xác thực và không có schema Joi bên TS — body được đọc thẳng từ
 * {@code req.body}, nên ở đây cũng nhận Map thay vì DTO có ràng buộc.
 */
@RestController
@RequestMapping("/v1/emails")
@RequiredArgsConstructor
public class EmailController {

    private final EmailService emailService;

    @PostMapping
    public ApiResponse<Void> sendOtp(@RequestBody Map<String, String> body) {
        emailService.sendOtp(body.get("email"));
        return ApiResponse.message(200, "Send email successfully");
    }

    @PostMapping("/verify")
    public ApiResponse<Void> verifyOtp(@RequestBody Map<String, String> body) {
        emailService.verify(body.get("email"), body.get("otp"));
        return ApiResponse.message(200, "Verify email successfully");
    }
}

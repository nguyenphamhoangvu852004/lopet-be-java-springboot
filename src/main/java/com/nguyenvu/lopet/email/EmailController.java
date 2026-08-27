package com.nguyenvu.lopet.email;

import com.nguyenvu.lopet.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Email management", description = "APIs used for sending OTP, OTP Verify)")
@RestController
@RequestMapping("/v1/emails")
@RequiredArgsConstructor
public class EmailController {

    private final EmailService emailService;

    @Operation(summary = "Send OTP into email provided")
    @PostMapping
    public ApiResponse<Void> sendOtp(@RequestBody String email) {
        emailService.sendOtp(email);
        return ApiResponse.message(200, "Send email successfully");
    }

    @Operation(summary = "Verify OTP that sent into email provided")
    @PostMapping("/verify")
    public ApiResponse<Void> verifyOtp(@RequestBody String email, @RequestBody String otp) {
        emailService.verify(email, otp);
        return ApiResponse.message(200, "Verify email successfully");
    }
}

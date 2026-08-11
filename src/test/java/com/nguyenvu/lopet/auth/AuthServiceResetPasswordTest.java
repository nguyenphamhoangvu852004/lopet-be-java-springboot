package com.nguyenvu.lopet.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.nguyenvu.lopet.account.entity.Account;
import com.nguyenvu.lopet.account.repository.AccountRepository;
import com.nguyenvu.lopet.auth.dto.ResetPasswordRequest;
import com.nguyenvu.lopet.common.exception.BadRequestException;
import com.nguyenvu.lopet.common.exception.ForbiddenException;
import com.nguyenvu.lopet.email.OtpStore;
import com.nguyenvu.lopet.security.jwt.JwtService;

/**
 * Bản port của {@code src/modules/auth/__tests__/resetPassword.test.ts} — chốt chặn hồi quy cho lỗ
 * hổng chiếm tài khoản qua {@code POST /v1/auth/reset}.
 *
 * <p>Trước khi vá, hàm này ghi thẳng mật khẩu mới chỉ dựa trên email do client gửi lên, không đòi
 * bằng chứng nào rằng người gọi kiểm soát hòm thư đó — bất kỳ ai biết địa chỉ email đều chiếm được
 * tài khoản tương ứng, kể cả tài khoản ADMIN được seed sẵn.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceResetPasswordTest {

    private static final String VICTIM_EMAIL = "victim@example.com";
    private static final String OLD_PASSWORD_HASH = "hash-mat-khau-cu";
    private static final String NEW_PASSWORD = "MatKhauMoi@2026";

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private JwtService jwtService;
    @Mock
    private OtpStore otpStore;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(10);

    private AuthService authService;
    private Account account;

    @BeforeEach
    void setUp() {
        account = Account.builder()
                .id(1)
                .email(VICTIM_EMAIL)
                .username("victim")
                .password(OLD_PASSWORD_HASH)
                .build();
        authService = new AuthService(accountRepository, passwordEncoder, jwtService, otpStore);
    }

    private ResetPasswordRequest request(String password) {
        return new ResetPasswordRequest(VICTIM_EMAIL, password, password);
    }

    @Test
    void tu_choi_khi_chua_xac_thuc_OTP_va_khong_dung_vao_mat_khau() {
        when(otpStore.consumeVerifiedFlag(VICTIM_EMAIL)).thenReturn(null);

        assertThatThrownBy(() -> authService.resetPassword(request(NEW_PASSWORD)))
                .isInstanceOf(ForbiddenException.class);

        // Tính chất quan trọng nhất: mật khẩu cũ còn nguyên, không có lệnh ghi nào
        assertThat(account.getPassword()).isEqualTo(OLD_PASSWORD_HASH);
        verify(accountRepository, never()).save(any());
    }

    @Test
    void cho_phep_doi_mat_khau_khi_da_co_co_xac_thuc_OTP() {
        when(otpStore.consumeVerifiedFlag(VICTIM_EMAIL)).thenReturn("true");
        when(accountRepository.findDetailByEmail(VICTIM_EMAIL)).thenReturn(Optional.of(account));
        when(accountRepository.save(any(Account.class))).thenAnswer(call -> call.getArgument(0));

        var result = authService.resetPassword(request(NEW_PASSWORD));

        assertThat(result.email()).isEqualTo(VICTIM_EMAIL);
        verify(accountRepository).save(account);
        assertThat(account.getPassword()).isNotEqualTo(OLD_PASSWORD_HASH);
        // Mật khẩu phải được băm, không lưu plaintext
        assertThat(account.getPassword()).isNotEqualTo(NEW_PASSWORD);
        assertThat(passwordEncoder.matches(NEW_PASSWORD, account.getPassword())).isTrue();
    }

    @Test
    void tieu_thu_co_xac_thuc_bang_mot_lenh_nguyen_tu() {
        when(otpStore.consumeVerifiedFlag(VICTIM_EMAIL)).thenReturn("true");
        when(accountRepository.findDetailByEmail(VICTIM_EMAIL)).thenReturn(Optional.of(account));
        when(accountRepository.save(any(Account.class))).thenAnswer(call -> call.getArgument(0));

        authService.resetPassword(request(NEW_PASSWORD));

        // GETDEL đọc-và-xoá trong một lệnh; tách get rồi del sẽ để hở cửa sổ cho hai request song
        // song cùng đi qua, tức một lần OTP đổi được nhiều lần mật khẩu.
        verify(otpStore).consumeVerifiedFlag(VICTIM_EMAIL);
        verify(otpStore, never()).findVerifiedFlag(anyString());
    }

    @Test
    void khong_doi_mat_khau_khi_confirmPassword_khong_khop() {
        ResetPasswordRequest mismatched =
                new ResetPasswordRequest(VICTIM_EMAIL, NEW_PASSWORD, "GoNhamRoi@2026");

        assertThatThrownBy(() -> authService.resetPassword(mismatched))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Mật khẩu xác nhận không khớp");

        assertThat(account.getPassword()).isEqualTo(OLD_PASSWORD_HASH);
        verify(accountRepository, never()).save(any());
        // Không khớp thì cờ OTP cũng chưa bị tiêu thụ — người dùng gõ lại được ngay
        verify(otpStore, never()).consumeVerifiedFlag(anyString());
    }
}

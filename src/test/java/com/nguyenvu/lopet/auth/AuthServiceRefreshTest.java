package com.nguyenvu.lopet.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.nguyenvu.lopet.account.entity.Account;
import com.nguyenvu.lopet.account.entity.AccountRole;
import com.nguyenvu.lopet.account.repository.AccountRepository;
import com.nguyenvu.lopet.common.exception.UnauthorizedException;
import com.nguyenvu.lopet.email.OtpStore;
import com.nguyenvu.lopet.role.entity.Role;
import com.nguyenvu.lopet.role.entity.RoleName;
import com.nguyenvu.lopet.security.jwt.JwtService;
import com.nguyenvu.lopet.security.jwt.UserPrincipal;

import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class AuthServiceRefreshTest {

    private static final String ACCESS_SECRET = "test-access-secret-23by";
    private static final String REFRESH_SECRET = "test-refresh-secret-24by";

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private OtpStore otpStore;

    private final JwtService jwtService = new JwtService(JsonMapper.builder().build(),
            ACCESS_SECRET, REFRESH_SECRET, 3600, 36000);

    private AuthService authService;
    private Account account;

    @BeforeEach
    void setUp() {
        account = Account.builder()
                .id(7)
                .email("user@lopet.local")
                .username("user")
                .isBanned(0)
                .build();
        authService = new AuthService(accountRepository, new BCryptPasswordEncoder(10), jwtService, otpStore);
    }

    private String refreshTokenOf(Account owner) {
        return jwtService.generateRefreshToken(new UserPrincipal(owner.getId(), owner.getEmail(), java.util.List.of()));
    }

    @Test
    void cap_lai_cap_token_moi_tu_refresh_token_hop_le() {
        when(accountRepository.findDetailById(7)).thenReturn(Optional.of(account));

        IssuedTokens result = authService.refresh(refreshTokenOf(account));

        assertThat(result.id()).isEqualTo(7);
        UserPrincipal decoded = jwtService.parseAccessToken(result.accessToken());
        assertThat(decoded.id()).isEqualTo(7);
        assertThat(decoded.email()).isEqualTo("user@lopet.local");
        assertThat(jwtService.parseRefreshToken(result.refreshToken()).id()).isEqualTo(7);
    }

    @Test
    void roles_duoc_doc_lai_tu_DB_chu_khong_lay_tu_token_cu() {
        String tokenKhongCoRole = refreshTokenOf(account);

        Set<AccountRole> roles = new LinkedHashSet<>();
        roles.add(AccountRole.builder()
                .role(Role.builder().id(1).name(RoleName.ADMIN).build())
                .build());
        account.setAccountRoles(roles);
        when(accountRepository.findDetailById(7)).thenReturn(Optional.of(account));

        IssuedTokens result = authService.refresh(tokenKhongCoRole);

        assertThat(jwtService.parseAccessToken(result.accessToken()).roles()).containsExactly("ADMIN");
    }

    @Test
    void tu_choi_access_token_dung_thay_cho_refresh_token() {
        String accessToken = jwtService.generateAccessToken(
                new UserPrincipal(7, "user@lopet.local", java.util.List.of()));

        assertThatThrownBy(() -> authService.refresh(accessToken))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Refresh token không hợp lệ");
    }

    @Test
    void tu_choi_refresh_token_da_het_han() {
        JwtService hetHan = new JwtService(JsonMapper.builder().build(),
                ACCESS_SECRET, REFRESH_SECRET, 3600, -1);
        String expired = hetHan.generateRefreshToken(new UserPrincipal(7, "user@lopet.local", java.util.List.of()));

        assertThatThrownBy(() -> authService.refresh(expired))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Refresh token đã hết hạn");
    }

    @Test
    void tu_choi_token_ky_bang_khoa_khac() {
        JwtService keGiaMao = new JwtService(JsonMapper.builder().build(),
                ACCESS_SECRET, "khoa-gia-mao-cua-ke-tan-cong", 3600, 36000);
        String forged = keGiaMao.generateRefreshToken(new UserPrincipal(7, "user@lopet.local", java.util.List.of()));

        assertThatThrownBy(() -> authService.refresh(forged))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void tai_khoan_bi_khoa_khong_gia_han_duoc() {
        String token = refreshTokenOf(account);
        account.setIsBanned(1);
        when(accountRepository.findDetailById(7)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> authService.refresh(token))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Người dùng user đã bị khoá");
    }

    @Test
    void thieu_refresh_token_tra_401() {
        assertThatThrownBy(() -> authService.refresh(null))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Refresh token không hợp lệ");
        assertThatThrownBy(() -> authService.refresh("   "))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Refresh token không hợp lệ");
    }

    @Test
    void tai_khoan_khong_con_ton_tai_thi_401() {
        String token = refreshTokenOf(account);
        when(accountRepository.findDetailById(7)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh(token))
                .isInstanceOf(UnauthorizedException.class);
    }
}

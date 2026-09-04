package com.nguyenvu.lopet.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.nguyenvu.lopet.account.entity.Account;
import com.nguyenvu.lopet.account.repository.AccountRepository;
import com.nguyenvu.lopet.common.exception.UnauthorizedException;
import com.nguyenvu.lopet.email.OtpStore;
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
        return jwtService.generateRefreshToken(new UserPrincipal(owner.getId(), owner.getEmail()));
    }

    @Test
    void issues_a_new_token_pair_from_a_valid_refresh_token() {
        when(accountRepository.findDetailById(7)).thenReturn(Optional.of(account));

        IssuedTokens result = authService.refresh(refreshTokenOf(account));

        assertThat(result.id()).isEqualTo(7);
        UserPrincipal decoded = jwtService.parseAccessToken(result.accessToken());
        assertThat(decoded.id()).isEqualTo(7);
        assertThat(decoded.email()).isEqualTo("user@lopet.local");
        assertThat(jwtService.parseRefreshToken(result.refreshToken()).id()).isEqualTo(7);
    }


    @Test
    void rejects_an_access_token_used_in_place_of_a_refresh_token() {
        String accessToken = jwtService.generateAccessToken(
                new UserPrincipal(7, "user@lopet.local"));

        assertThatThrownBy(() -> authService.refresh(accessToken))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid refresh token");
    }

    @Test
    void rejects_an_expired_refresh_token() {
        JwtService hetHan = new JwtService(JsonMapper.builder().build(),
                ACCESS_SECRET, REFRESH_SECRET, 3600, -1);
        String expired = hetHan.generateRefreshToken(new UserPrincipal(7, "user@lopet.local"));

        assertThatThrownBy(() -> authService.refresh(expired))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Refresh token has expired");
    }

    @Test
    void rejects_a_token_signed_with_another_key() {
        JwtService keGiaMao = new JwtService(JsonMapper.builder().build(),
                ACCESS_SECRET, "khoa-gia-mao-cua-ke-tan-cong", 3600, 36000);
        String forged = keGiaMao.generateRefreshToken(new UserPrincipal(7, "user@lopet.local"));

        assertThatThrownBy(() -> authService.refresh(forged))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void a_banned_account_cannot_refresh() {
        String token = refreshTokenOf(account);
        account.setIsBanned(1);
        when(accountRepository.findDetailById(7)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> authService.refresh(token))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("User user is banned");
    }

    @Test
    void a_missing_refresh_token_returns_401() {
        assertThatThrownBy(() -> authService.refresh(null))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid refresh token");
        assertThatThrownBy(() -> authService.refresh("   "))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid refresh token");
    }

    @Test
    void an_account_that_no_longer_exists_returns_401() {
        String token = refreshTokenOf(account);
        when(accountRepository.findDetailById(7)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh(token))
                .isInstanceOf(UnauthorizedException.class);
    }
}

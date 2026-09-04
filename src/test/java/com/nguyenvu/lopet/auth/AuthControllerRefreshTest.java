package com.nguyenvu.lopet.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.nguyenvu.lopet.common.exception.GlobalExceptionHandler;
import com.nguyenvu.lopet.common.exception.UnauthorizedException;
import com.nguyenvu.lopet.security.AuthInterceptor;

import jakarta.servlet.http.Cookie;

@ExtendWith(MockitoExtension.class)
class AuthControllerRefreshTest {

    private static final String COOKIE = "refreshToken";

    @Mock
    private AuthService authService;

    private final RefreshTokenCookie refreshTokenCookie =
            new RefreshTokenCookie(COOKIE, "/", false, "Lax", 36000);

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService, refreshTokenCookie))
                .addInterceptors(new AuthInterceptor())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void refreshes_even_when_the_access_token_is_broken() throws Exception {
        when(authService.refresh("valid-token"))
                .thenReturn(new IssuedTokens(7, "new-access", "new-refresh"));

        mockMvc.perform(post("/v1/auth/refresh")
                        .cookie(new Cookie(COOKIE, "valid-token"))
                        .header("Authorization", "Bearer broken-access-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.data.accessToken").value("new-access"))
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andExpect(cookie().value(COOKIE, "new-refresh"))
                .andExpect(cookie().httpOnly(COOKIE, true));
    }

    @Test
    void broken_refresh_token_returns_401_so_the_client_clears_the_session() throws Exception {
        when(authService.refresh(any())).thenThrow(new UnauthorizedException("Refresh token has expired"));

        mockMvc.perform(post("/v1/auth/refresh")
                        .cookie(new Cookie(COOKIE, "expired-token")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Refresh token has expired"));
    }

    @Test
    void a_missing_cookie_reaches_the_service_as_null() throws Exception {
        when(authService.refresh(isNull())).thenThrow(new UnauthorizedException("Invalid refresh token"));

        mockMvc.perform(post("/v1/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid refresh token"));
    }

    @Test
    void login_returns_the_refresh_token_in_an_httponly_cookie_not_in_the_body() throws Exception {
        when(authService.login(any())).thenReturn(new IssuedTokens(7, "new-access", "new-refresh"));

        MockHttpServletResponse response = mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"user\",\"password\":\"secret\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(7))
                .andExpect(jsonPath("$.data.accessToken").value("new-access"))
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andExpect(cookie().value(COOKIE, "new-refresh"))
                .andExpect(cookie().httpOnly(COOKIE, true))
                .andExpect(cookie().maxAge(COOKIE, 36000))
                .andExpect(cookie().path(COOKIE, "/"))
                .andReturn().getResponse();

        assertThat(response.getCookie(COOKIE).getAttribute("SameSite")).isEqualTo("Lax");
    }
}

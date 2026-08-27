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
import com.nguyenvu.lopet.security.jwt.JwtAuthenticationFilter;

import jakarta.servlet.http.Cookie;

/**
 * Dựng MockMvc standalone kèm ĐÚNG {@link AuthInterceptor} thật, vì điều cần chốt ở đây là chuyện
 * định tuyến/chặn chứ không phải nghiệp vụ: người gọi {@code /v1/auth/refresh} luôn là người có
 * access token đã hỏng hoặc hết hạn, nên endpoint này KHÔNG được đòi token hợp lệ. Gắn nhầm
 * {@code @Auth} vào đây sẽ khoá vĩnh viễn đường gia hạn — và không test nào khác bắt được.
 *
 * <p>Dùng {@link RefreshTokenCookie} thật chứ không mock: nửa còn lại cần chốt là refresh token
 * đi vào cookie và KHÔNG lọt ra body.
 */
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
    void gia_han_duoc_ngay_ca_khi_access_token_da_hong() throws Exception {
        when(authService.refresh("token-hop-le"))
                .thenReturn(new IssuedTokens(7, "access-moi", "refresh-moi"));

        mockMvc.perform(post("/v1/auth/refresh")
                        .cookie(new Cookie(COOKIE, "token-hop-le"))
                        // Trạng thái mà filter để lại khi Authorization mang token hết hạn
                        .requestAttr(JwtAuthenticationFilter.ATTRIBUTE_STATE,
                                JwtAuthenticationFilter.TokenState.INVALID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.data.accessToken").value("access-moi"))
                // Token xoay vòng ra cookie, không ra body
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andExpect(cookie().value(COOKIE, "refresh-moi"))
                .andExpect(cookie().httpOnly(COOKIE, true));
    }

    @Test
    void refresh_token_hong_tra_401_de_client_xoa_phien() throws Exception {
        when(authService.refresh(any())).thenThrow(new UnauthorizedException("Refresh token đã hết hạn"));

        mockMvc.perform(post("/v1/auth/refresh")
                        .cookie(new Cookie(COOKIE, "token-het-han")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Refresh token đã hết hạn"));
    }

    /**
     * Không còn body để validate: thiếu refresh token nghĩa là thiếu cookie, và quyết định thuộc về
     * service — controller chỉ có nhiệm vụ chuyển {@code null} xuống thay vì tự dựng lỗi riêng.
     */
    @Test
    void thieu_cookie_thi_service_nhan_null() throws Exception {
        when(authService.refresh(isNull())).thenThrow(new UnauthorizedException("Refresh token không hợp lệ"));

        mockMvc.perform(post("/v1/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Refresh token không hợp lệ"));
    }

    @Test
    void dang_nhap_tra_refresh_token_qua_cookie_httponly_chu_khong_qua_body() throws Exception {
        when(authService.login(any())).thenReturn(new IssuedTokens(7, "access-moi", "refresh-moi"));

        MockHttpServletResponse response = mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"user\",\"password\":\"secret\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(7))
                .andExpect(jsonPath("$.data.accessToken").value("access-moi"))
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andExpect(cookie().value(COOKIE, "refresh-moi"))
                .andExpect(cookie().httpOnly(COOKIE, true))
                // Cookie sống đúng bằng TTL của refresh token
                .andExpect(cookie().maxAge(COOKIE, 36000))
                .andExpect(cookie().path(COOKIE, "/"))
                .andReturn().getResponse();

        assertThat(response.getCookie(COOKIE).getAttribute("SameSite")).isEqualTo("Lax");
    }
}

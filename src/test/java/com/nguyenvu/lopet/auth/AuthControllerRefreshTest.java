package com.nguyenvu.lopet.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.nguyenvu.lopet.auth.dto.RefreshTokenRequest;
import com.nguyenvu.lopet.auth.dto.RefreshTokenResponse;
import com.nguyenvu.lopet.common.exception.GlobalExceptionHandler;
import com.nguyenvu.lopet.common.exception.UnauthorizedException;
import com.nguyenvu.lopet.security.AuthInterceptor;
import com.nguyenvu.lopet.security.jwt.JwtAuthenticationFilter;

/**
 * Dựng MockMvc standalone kèm ĐÚNG {@link AuthInterceptor} thật, vì điều cần chốt ở đây là chuyện
 * định tuyến/chặn chứ không phải nghiệp vụ: người gọi {@code /v1/auth/refresh} luôn là người có
 * access token đã hỏng hoặc hết hạn, nên endpoint này KHÔNG được đòi token hợp lệ. Gắn nhầm
 * {@code @Auth} vào đây sẽ khoá vĩnh viễn đường gia hạn — và không test nào khác bắt được.
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerRefreshTest {

    @Mock
    private AuthService authService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService))
                .addInterceptors(new AuthInterceptor())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void gia_han_duoc_ngay_ca_khi_access_token_da_hong() throws Exception {
        when(authService.refresh(any())).thenReturn(new RefreshTokenResponse(7, "access-moi", "refresh-moi"));

        mockMvc.perform(post("/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        // Trạng thái mà filter để lại khi Authorization mang token hết hạn
                        .requestAttr(JwtAuthenticationFilter.ATTRIBUTE_STATE,
                                JwtAuthenticationFilter.TokenState.INVALID)
                        .content("{\"refreshToken\":\"token-hop-le\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.data.accessToken").value("access-moi"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh-moi"));
    }

    @Test
    void refresh_token_hong_tra_401_de_client_xoa_phien() throws Exception {
        when(authService.refresh(any())).thenThrow(new UnauthorizedException("Refresh token đã hết hạn"));

        mockMvc.perform(post("/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"token-het-han\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Refresh token đã hết hạn"));
    }

    @Test
    void thieu_refreshToken_tra_loi_validate_dang_Joi() throws Exception {
        mockMvc.perform(post("/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation error"))
                .andExpect(jsonPath("$.errors[0].field").value("refreshToken"));

        verify(authService, never()).refresh(any(RefreshTokenRequest.class));
    }
}

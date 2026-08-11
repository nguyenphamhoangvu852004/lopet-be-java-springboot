package com.nguyenvu.lopet.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

/**
 * JwtService được tự hiện thực (không dùng jjwt/Nimbus) để chấp nhận được khoá ngắn của
 * {@code .env} hiện tại, nên phần mã hoá phải có test riêng — đây là chỗ dễ sai nhất.
 */
class JwtServiceTest {

    /**
     * Đúng độ dài khoá đang chạy ở production: 23 byte, ngắn hơn mức RFC 7518 đòi cho HS256.
     * Giá trị chỉ cần đúng độ dài — không lặp lại secret thật, thứ chỉ được sống trong {@code .env}.
     */
    private static final String SHORT_SECRET = "test-access-secret-23by";

    private final JwtService jwtService = new JwtService(JsonMapper.builder().build(),
            SHORT_SECRET, "test-refresh-secret-24by", 3600, 36000);

    private final UserPrincipal principal = new UserPrincipal(7, "user@lopet.local", List.of("ADMIN"));

    @Test
    void sinh_va_giai_ma_token_giu_nguyen_payload() {
        UserPrincipal decoded = jwtService.parseAccessToken(jwtService.generateAccessToken(principal));

        assertThat(decoded.id()).isEqualTo(7);
        assertThat(decoded.email()).isEqualTo("user@lopet.local");
        assertThat(decoded.roles()).containsExactly("ADMIN");
    }

    @Test
    void header_giong_het_thu_vien_jsonwebtoken() {
        String token = jwtService.generateAccessToken(principal);
        String header = new String(Base64.getUrlDecoder().decode(token.split("\\.")[0]));

        // jsonwebtoken sinh đúng chuỗi này; client nào tự kiểm header sẽ dựa vào nó
        assertThat(header).isEqualTo("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
    }

    @Test
    void payload_mang_du_iat_va_exp() {
        String token = jwtService.generateAccessToken(principal);
        String body = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]));

        assertThat(body).contains("\"id\":7").contains("\"iat\":").contains("\"exp\":");
    }

    @Test
    void token_bi_sua_chu_ky_bi_tu_choi() {
        String token = jwtService.generateAccessToken(principal);
        String tampered = token.substring(0, token.lastIndexOf('.') + 1) + "AAAAAAAAAAAAAAAAAAAAAA";

        assertThatThrownBy(() -> jwtService.parseAccessToken(tampered))
                .isInstanceOf(JwtException.class)
                .hasMessage(JwtException.INVALID_SIGNATURE);
    }

    @Test
    void token_bi_sua_payload_bi_tu_choi() {
        String token = jwtService.generateAccessToken(principal);
        String[] parts = token.split("\\.");
        String forged = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"id\":1,\"email\":\"admin@lopet.local\",\"roles\":[\"ADMIN\"]}".getBytes());

        assertThatThrownBy(() -> jwtService.parseAccessToken(parts[0] + "." + forged + "." + parts[2]))
                .isInstanceOf(JwtException.class)
                .hasMessage(JwtException.INVALID_SIGNATURE);
    }

    @Test
    void token_ky_bang_khoa_khac_bi_tu_choi() {
        JwtService other = new JwtService(JsonMapper.builder().build(),
                "mot-khoa-hoan-toan-khac", "refresh", 3600, 36000);

        assertThatThrownBy(() -> jwtService.parseAccessToken(other.generateAccessToken(principal)))
                .isInstanceOf(JwtException.class)
                .hasMessage(JwtException.INVALID_SIGNATURE);
    }

    @Test
    void token_het_han_bao_dung_thong_diep_cua_jsonwebtoken() {
        JwtService expired = new JwtService(JsonMapper.builder().build(), SHORT_SECRET, "refresh", -10, -10);

        assertThatThrownBy(() -> expired.parseAccessToken(expired.generateAccessToken(principal)))
                .isInstanceOf(JwtException.class)
                .hasMessage(JwtException.EXPIRED);
    }

    @Test
    void chuoi_khong_phai_jwt_bi_coi_la_malformed() {
        assertThatThrownBy(() -> jwtService.parseAccessToken("khong-phai-token"))
                .isInstanceOf(JwtException.class)
                .hasMessage(JwtException.MALFORMED);
    }

    @Test
    void access_token_va_refresh_token_khong_dung_chung_khoa() {
        // Refresh token ký bằng REFRESH_TOKEN_SECRET nên không được dùng thay access token
        assertThatThrownBy(() -> jwtService.parseAccessToken(jwtService.generateRefreshToken(principal)))
                .isInstanceOf(JwtException.class)
                .hasMessage(JwtException.INVALID_SIGNATURE);
    }

    @Test
    void roles_khong_phai_mang_thi_coi_nhu_rong() {
        JwtService service = new JwtService(JsonMapper.builder().build(), SHORT_SECRET, "refresh", 3600, 36000);
        UserPrincipal khongCoRole = new UserPrincipal(3, "a@b.local", null);

        assertThat(service.parseAccessToken(service.generateAccessToken(khongCoRole)).roles()).isEmpty();
    }
}

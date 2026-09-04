package com.nguyenvu.lopet.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

class JwtServiceTest {

    private static final String SHORT_SECRET = "test-access-secret-23by";

    private final JwtService jwtService = new JwtService(JsonMapper.builder().build(),
            SHORT_SECRET, "test-refresh-secret-24by", 3600, 36000);

    private final UserPrincipal principal = new UserPrincipal(7, "user@lopet.local");

    @Test
    void generates_and_parses_token_preserving_payload() {
        UserPrincipal decoded = jwtService.parseAccessToken(jwtService.generateAccessToken(principal));

        assertThat(decoded.id()).isEqualTo(7);
        assertThat(decoded.email()).isEqualTo("user@lopet.local");
    }

    @Test
    void header_matches_the_jsonwebtoken_library() {
        String token = jwtService.generateAccessToken(principal);
        String header = new String(Base64.getUrlDecoder().decode(token.split("\\.")[0]));

        assertThat(header).isEqualTo("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
    }

    @Test
    void payload_carries_both_iat_and_exp() {
        String token = jwtService.generateAccessToken(principal);
        String body = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]));

        assertThat(body).contains("\"id\":7").contains("\"iat\":").contains("\"exp\":");
    }

    @Test
    void token_with_tampered_signature_is_rejected() {
        String token = jwtService.generateAccessToken(principal);
        String tampered = token.substring(0, token.lastIndexOf('.') + 1) + "AAAAAAAAAAAAAAAAAAAAAA";

        assertThatThrownBy(() -> jwtService.parseAccessToken(tampered))
                .isInstanceOf(JwtException.class)
                .hasMessage(JwtException.INVALID_SIGNATURE);
    }

    @Test
    void token_with_tampered_payload_is_rejected() {
        String token = jwtService.generateAccessToken(principal);
        String[] parts = token.split("\\.");
        String forged = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"id\":1,\"email\":\"admin@lopet.local\"}".getBytes());

        assertThatThrownBy(() -> jwtService.parseAccessToken(parts[0] + "." + forged + "." + parts[2]))
                .isInstanceOf(JwtException.class)
                .hasMessage(JwtException.INVALID_SIGNATURE);
    }

    @Test
    void token_signed_with_another_key_is_rejected() {
        JwtService other = new JwtService(JsonMapper.builder().build(),
                "a-completely-different-key", "refresh", 3600, 36000);

        assertThatThrownBy(() -> jwtService.parseAccessToken(other.generateAccessToken(principal)))
                .isInstanceOf(JwtException.class)
                .hasMessage(JwtException.INVALID_SIGNATURE);
    }

    @Test
    void expired_token_reports_the_jsonwebtoken_message() {
        JwtService expired = new JwtService(JsonMapper.builder().build(), SHORT_SECRET, "refresh", -10, -10);

        assertThatThrownBy(() -> expired.parseAccessToken(expired.generateAccessToken(principal)))
                .isInstanceOf(JwtException.class)
                .hasMessage(JwtException.EXPIRED);
    }

    @Test
    void a_string_that_is_not_a_jwt_is_treated_as_malformed() {
        assertThatThrownBy(() -> jwtService.parseAccessToken("not-a-token"))
                .isInstanceOf(JwtException.class)
                .hasMessage(JwtException.MALFORMED);
    }

    @Test
    void access_token_and_refresh_token_do_not_share_a_key() {
        assertThatThrownBy(() -> jwtService.parseAccessToken(jwtService.generateRefreshToken(principal)))
                .isInstanceOf(JwtException.class)
                .hasMessage(JwtException.INVALID_SIGNATURE);
    }

}

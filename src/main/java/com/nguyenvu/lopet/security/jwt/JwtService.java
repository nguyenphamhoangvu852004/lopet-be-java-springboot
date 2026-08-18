package com.nguyenvu.lopet.security.jwt;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Sinh và kiểm JWT HS256 tương thích từng byte với {@code jsonwebtoken} của lopet-be.
 *
 * <p>Vì sao tự hiện thực thay vì dùng jjwt/Nimbus: cả hai đều từ chối khoá HMAC ngắn hơn 256 bit
 * theo RFC 7518 §3.2, còn {@code ACCESS_TOKEN_SECRET} đang chạy chỉ dài 23 byte. Ép đổi khoá đồng
 * nghĩa file {@code .env} hiện tại không dùng lại được — mất tính thay thế trực tiếp.
 *
 * <p>Payload giữ đúng thứ tự và tên khoá của bản TS: {@code {id, email, roles, iat, exp}}.
 * Header cố định {@code {"alg":"HS256","typ":"JWT"}} — cũng chính là header jsonwebtoken sinh ra.
 */
@Service
public class JwtService {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    /** Chuỗi này là base64url của {"alg":"HS256","typ":"JWT"} — trùng với jsonwebtoken. */
    private static final String HEADER = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9";

    private final ObjectMapper objectMapper;
    private final byte[] accessSecret;
    private final byte[] refreshSecret;
    private final long accessTtlSeconds;
    private final long refreshTtlSeconds;

    public JwtService(ObjectMapper objectMapper,
                      @Value("${lopet.jwt.access-token-secret}") String accessSecret,
                      @Value("${lopet.jwt.refresh-token-secret}") String refreshSecret,
                      @Value("${lopet.jwt.access-token-expires-in}") long accessTtlSeconds,
                      @Value("${lopet.jwt.refresh-token-expires-in}") long refreshTtlSeconds) {
        this.objectMapper = objectMapper;
        this.accessSecret = accessSecret.getBytes(StandardCharsets.UTF_8);
        this.refreshSecret = refreshSecret.getBytes(StandardCharsets.UTF_8);
        this.accessTtlSeconds = accessTtlSeconds;
        this.refreshTtlSeconds = refreshTtlSeconds;
    }

    public String generateAccessToken(UserPrincipal payload) {
        return sign(payload, accessSecret, accessTtlSeconds);
    }

    public String generateRefreshToken(UserPrincipal payload) {
        return sign(payload, refreshSecret, refreshTtlSeconds);
    }

    /**
     * Giải mã access token. Ném {@link JwtException} với đúng message mà jsonwebtoken dùng, vì các
     * tầng trên phân biệt "hết hạn" với "sai chữ ký" theo chuỗi đó.
     */
    public UserPrincipal parseAccessToken(String token) {
        return parse(token, accessSecret);
    }

    /**
     * Giải mã refresh token. Khóa ký khác access token nên một access token còn hạn KHÔNG dùng được
     * ở {@code POST /v1/auth/refresh}, và ngược lại refresh token không qua được
     * {@link com.nguyenvu.lopet.security.jwt.JwtAuthenticationFilter} để gọi API thường — đó là
     * toàn bộ phần tách bạch giữa hai loại token ở bản không lưu trạng thái này.
     */
    public UserPrincipal parseRefreshToken(String token) {
        return parse(token, refreshSecret);
    }

    private String sign(UserPrincipal payload, byte[] secret, long ttlSeconds) {
        long issuedAt = Instant.now().getEpochSecond();

        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("id", payload.id());
        claims.put("email", payload.email());
        claims.put("roles", payload.roles());
        claims.put("iat", issuedAt);
        claims.put("exp", issuedAt + ttlSeconds);

        String body = ENCODER.encodeToString(writeJson(claims));
        String signingInput = HEADER + "." + body;
        return signingInput + "." + ENCODER.encodeToString(hmac(signingInput, secret));
    }

    private UserPrincipal parse(String token, byte[] secret) {
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3 || parts[0].isEmpty() || parts[1].isEmpty()) {
            throw new JwtException(JwtException.MALFORMED);
        }

        byte[] expected = hmac(parts[0] + "." + parts[1], secret);
        byte[] actual;
        byte[] claimBytes;
        try {
            actual = DECODER.decode(parts[2]);
            claimBytes = DECODER.decode(parts[1]);
        } catch (IllegalArgumentException exception) {
            throw new JwtException(JwtException.MALFORMED);
        }

        // isEqual so sánh trong thời gian hằng — tránh rò rỉ chữ ký đúng qua thời gian phản hồi
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new JwtException(JwtException.INVALID_SIGNATURE);
        }

        Map<String, Object> claims;
        try {
            claims = objectMapper.readValue(claimBytes, new TypeReference<>() {
            });
        } catch (Exception exception) {
            throw new JwtException(JwtException.MALFORMED);
        }

        Object exp = claims.get("exp");
        if (exp instanceof Number expiry && Instant.now().getEpochSecond() >= expiry.longValue()) {
            throw new JwtException(JwtException.EXPIRED, true);
        }

        return new UserPrincipal(toAccountId(claims.get("id")), asString(claims.get("email")), toRoles(claims.get("roles")));
    }

    private byte[] writeJson(Map<String, Object> claims) {
        try {
            return objectMapper.writeValueAsBytes(claims);
        } catch (Exception exception) {
            throw new IllegalStateException("Không serialize được payload JWT", exception);
        }
    }

    private byte[] hmac(String signingInput, byte[] secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret, HMAC_SHA256));
            return mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("Không ký được JWT", exception);
        }
    }

    /** Khoá chính của accounts là int nên id trong token cũng thu về Integer */
    private static Integer toAccountId(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private static String asString(Object value) {
        return value instanceof String text ? text : null;
    }

    /**
     * Giữ đúng cách đọc của {@code verifyToken()}: {@code Array.isArray(decoded.roles) ? … : []} —
     * roles không phải mảng thì coi như không có role nào, không ném lỗi.
     */
    private static List<String> toRoles(Object value) {
        if (!(value instanceof List<?> raw)) {
            return List.of();
        }
        List<String> roles = new ArrayList<>(raw.size());
        for (Object item : raw) {
            if (item instanceof String role) {
                roles.add(role);
            }
        }
        return roles;
    }
}

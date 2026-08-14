package com.nguyenvu.lopet.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.nguyenvu.lopet.security.jwt.JwtAuthenticationFilter;

import lombok.RequiredArgsConstructor;

/**
 * Chuỗi filter cố ý để {@code permitAll}: mọi quyết định phân quyền nằm ở bốn tầng đã có
 * ({@link Auth}, {@link RequirePermission}, ownership, capability) để trùng đúng một nguồn sự thật
 * với backend TypeScript. Dùng thêm {@code authorizeHttpRequests} theo pattern URL sẽ tạo ra tầng
 * thứ năm, và mỗi lần thêm route lại có nguy cơ hai tầng nói khác nhau.
 */
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) {
        return http
                .csrf(csrf -> csrf.disable())
                .formLogin(login -> login.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    /**
     * bcrypt cost 10 — trùng {@code bcryptConfig.saltRounds} của lopet-be, và cùng định dạng
     * {@code $2a$} nên hash cũ trong DB vẫn kiểm được, người dùng không phải đặt lại mật khẩu.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }
}

package com.nguyenvu.lopet.bootstrap;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.nguyenvu.lopet.account.AccountService;
import com.nguyenvu.lopet.account.entity.Account;
import com.nguyenvu.lopet.account.repository.AccountRepository;
import com.nguyenvu.lopet.common.exception.BadRequestException;
import com.nguyenvu.lopet.accountprofile.AccountProfileFactory;
import com.nguyenvu.lopet.role.entity.RoleName;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Bản dịch của {@code InitAdmin}: tạo tài khoản từ {@code INIT_ADMIN_*} nếu chưa có, rồi gán role
 * ADMIN cho nó (gán lại mỗi lần khởi động, kể cả khi tài khoản đã tồn tại).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminInitializer {

    private final AccountRepository accountRepository;
    private final AccountService accountService;
    private final PasswordEncoder passwordEncoder;

    @Value("${lopet.admin.email:}")
    private String email;

    @Value("${lopet.admin.username:}")
    private String username;

    @Value("${lopet.admin.password:}")
    private String password;

    @Transactional
    public void init() {
        // Cấu hình trống (môi trường test) thì bỏ qua thay vì tạo tài khoản rỗng rồi vỡ ràng buộc
        // unique — bản TS không gặp nhánh này vì .env luôn có sẵn ba biến.
        if (email.isBlank() || username.isBlank() || password.isBlank()) {
            log.info("Bỏ qua khởi tạo admin: INIT_ADMIN_* chưa được cấu hình");
            return;
        }

        // Đường tạo tài khoản THỨ HAI, không đi qua AuthService.register. Thiếu profile ở đây thì
        // tài khoản admin rơi đúng vào trạng thái "account không có hồ sơ" mà refactor vừa xoá bỏ.
        if (!accountRepository.existsByEmail(email)) {
            accountRepository.save(Account.builder()
                    .email(email)
                    .username(username)
                    .password(passwordEncoder.encode(password))
                    .isBanned(0)
                    .accountProfile(AccountProfileFactory.seedFor(username))
                    .build());
            log.info("Đã tạo tài khoản admin khởi tạo: {}", email);
        }

        Account admin = accountRepository.findDetailByEmail(email)
                .orElseThrow(() -> new BadRequestException("No Admin to set role"));
        accountService.setRoles(admin.getId(), List.of(RoleName.ADMIN.name()), null);
    }
}

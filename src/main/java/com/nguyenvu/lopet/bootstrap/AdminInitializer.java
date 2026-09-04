package com.nguyenvu.lopet.bootstrap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.nguyenvu.lopet.account.entity.Account;
import com.nguyenvu.lopet.account.repository.AccountRepository;
import com.nguyenvu.lopet.accountprofile.AccountProfileFactory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminInitializer {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${lopet.admin.email:}")
    private String email;

    @Value("${lopet.admin.username:}")
    private String username;

    @Value("${lopet.admin.password:}")
    private String password;

    @Transactional
    public void execute() {
        if (email.isBlank() || username.isBlank() || password.isBlank()) {
            log.info("Skipping admin bootstrap: INIT_ADMIN_* is not configured");
            return;
        }

        if (!accountRepository.existsByEmail(email)) {
            accountRepository.save(Account.builder()
                    .email(email)
                    .username(username)
                    .password(passwordEncoder.encode(password))
                    .isBanned(0)
                    .accountProfile(AccountProfileFactory.seedFor(username))
                    .build());
            log.info("Bootstrapped admin account created: {}", email);
        }
    }
}

package com.nguyenvu.lopet.bootstrap;

import com.nguyenvu.lopet.account.entity.Account;
import com.nguyenvu.lopet.account.repository.AccountRepository;
import com.nguyenvu.lopet.accountprofile.AccountProfileFactory;
import com.nguyenvu.lopet.auth.dto.RegisterRequest;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class SeedDemoAccount {

    public static final String DEMO_EMAIL = "nphvu.dev@gmail.com";

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;

    public SeedDemoAccount(AccountRepository accountRepository, PasswordEncoder passwordEncoder) {
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void execute() {
        if (accountRepository.existsByEmail(DEMO_EMAIL)) {
            log.debug("Bỏ qua seed tài khoản demo: {} đã tồn tại", DEMO_EMAIL);
            return;
        }

        String lopetPasswordForBootstrappedAcocunt = "LopetPass!2004";
        List<RegisterRequest> listRequestDto = new ArrayList<>(
                List.of(new RegisterRequest(DEMO_EMAIL, "nphvudev", lopetPasswordForBootstrappedAcocunt, lopetPasswordForBootstrappedAcocunt))
        );
        accountRepository.saveAll(
                listRequestDto.stream().map(dto -> Account.builder()
                        .email(dto.email())
                        .username(dto.username())
                        .password(passwordEncoder.encode(dto.password()))
                        .isBanned(0)
                        .accountProfile(AccountProfileFactory.seedFor(dto.username()))
                        .build()).toList());
        log.info("Đã seed tài khoản demo: {}", DEMO_EMAIL);
    }
}

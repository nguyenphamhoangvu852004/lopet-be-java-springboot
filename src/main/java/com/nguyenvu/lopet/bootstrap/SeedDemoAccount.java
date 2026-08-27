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

/**
 * Tài khoản demo cho môi trường dev. {@link StartupRunner} chỉ gọi seeder này khi
 * {@code lopet.bootstrap.seed-demo=true}, và cờ đó chỉ bật ở profile {@code dev}.
 */
@Slf4j
@Component
public class SeedDemoAccount {

    /** Email dùng làm khoá nhận diện tài khoản demo — {@link SeedDemoPosts} tra cứu lại bằng nó. */
    public static final String DEMO_EMAIL = "nphvu.dev@gmail.com";

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;

    public SeedDemoAccount(AccountRepository accountRepository, PasswordEncoder passwordEncoder) {
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void execute() {
        // Idempotent: seeder chạy ở MỌI lần khởi động, còn database dev thì sống qua nhiều lần
        // restart nhờ volume. Thiếu nhánh này thì lần chạy thứ hai đâm vào ràng buộc unique của
        // cột email và làm hỏng cả tiến trình khởi động.
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

package com.nguyenvu.lopet.auth;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nguyenvu.lopet.account.entity.Account;
import com.nguyenvu.lopet.account.repository.AccountRepository;
import com.nguyenvu.lopet.auth.dto.LoginRequest;
import com.nguyenvu.lopet.auth.dto.RegisterRequest;
import com.nguyenvu.lopet.auth.dto.RegisterResponse;
import com.nguyenvu.lopet.auth.dto.ResetPasswordRequest;
import com.nguyenvu.lopet.auth.dto.ResetPasswordResponse;
import com.nguyenvu.lopet.auth.dto.VerifyAccountRequest;
import com.nguyenvu.lopet.auth.dto.VerifyAccountResponse;
import com.nguyenvu.lopet.common.exception.BadRequestException;
import com.nguyenvu.lopet.common.exception.ConflictException;
import com.nguyenvu.lopet.common.exception.ForbiddenException;
import com.nguyenvu.lopet.common.exception.NotFoundException;
import com.nguyenvu.lopet.common.exception.UnauthorizedException;
import com.nguyenvu.lopet.email.OtpStore;
import com.nguyenvu.lopet.accountprofile.AccountProfileFactory;
import com.nguyenvu.lopet.security.jwt.JwtException;
import com.nguyenvu.lopet.security.jwt.JwtService;
import com.nguyenvu.lopet.security.jwt.UserPrincipal;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final OtpStore otpStore;

    @Transactional(readOnly = true)
    public IssuedTokens login(LoginRequest request) {
        Account account = accountRepository.findDetailByUsername(request.username())
                .orElseThrow(() -> new NotFoundException("No user found"));

        if (account.getIsBanned() != null && account.getIsBanned() == 1) {
            throw new BadRequestException("User " + account.getUsername() + " is banned");
        }
        if (!passwordEncoder.matches(request.password(), account.getPassword())) {
            throw new BadRequestException("Password does not match");
        }

        UserPrincipal payload = new UserPrincipal(account.getId(), account.getEmail());
        return new IssuedTokens(account.getId(),
                jwtService.generateAccessToken(payload),
                jwtService.generateRefreshToken(payload));
    }

    @Transactional(readOnly = true)
    public IssuedTokens refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new UnauthorizedException("Invalid refresh token");
        }

        UserPrincipal claims;
        try {
            claims = jwtService.parseRefreshToken(refreshToken);
        } catch (JwtException exception) {
            throw new UnauthorizedException(exception.isExpired()
                    ? "Refresh token has expired"
                    : "Invalid refresh token");
        }

        if (claims.id() == null) {
            throw new UnauthorizedException("Invalid refresh token");
        }

        Account account = accountRepository.findDetailById(claims.id())
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        if (account.getIsBanned() != null && account.getIsBanned() == 1) {
            throw new UnauthorizedException("User " + account.getUsername() + " is banned");
        }

        UserPrincipal payload = new UserPrincipal(account.getId(), account.getEmail());
        return new IssuedTokens(account.getId(),
                jwtService.generateAccessToken(payload),
                jwtService.generateRefreshToken(payload));
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String verified = otpStore.findVerifiedFlag(request.email());
        if (verified == null) {
            throw new BadRequestException("You must verify the OTP before registering.");
        }
        otpStore.deleteVerifiedFlag(request.email());

        if (accountRepository.findDetailByEmail(request.email()).isPresent()) {
            throw new ConflictException();
        }
        if (accountRepository.findDetailByUsername(request.username()).isPresent()) {
            throw new ConflictException();
        }
        if (!request.password().equals(request.confirmPassword())) {
            throw new BadRequestException();
        }

        Account saved = accountRepository.save(Account.builder()
                .email(request.email())
                .username(request.username())
                .password(passwordEncoder.encode(request.password()))
                .isBanned(0)
                .accountProfile(AccountProfileFactory.seedFor(request.username()))
                .build());

        return new RegisterResponse(saved.getId(), saved.getEmail(), saved.getUsername());
    }

    @Transactional
    public ResetPasswordResponse resetPassword(ResetPasswordRequest request) {
        if (!request.password().equals(request.confirmPassword())) {
            throw new BadRequestException("Password confirmation does not match");
        }

        String verified = otpStore.consumeVerifiedFlag(request.email());
        if (verified == null) {
            throw new ForbiddenException("You must verify the OTP before changing the password.");
        }

        Account account = accountRepository.findDetailByEmail(request.email())
                .orElseThrow(NotFoundException::new);

        account.setPassword(passwordEncoder.encode(request.password()));
        Account saved = accountRepository.save(account);

        return new ResetPasswordResponse(saved.getId(), saved.getEmail(), saved.getUsername());
    }

    @Transactional(readOnly = true)
    public VerifyAccountResponse verifyAccount(VerifyAccountRequest request) {
        Account account = accountRepository.findDetailByEmail(request.email())
                .orElseThrow(() -> new NotFoundException("Account not found"));

        if (!passwordEncoder.matches(request.password(), account.getPassword())) {
            throw new BadRequestException("Wrong password");
        }
        return new VerifyAccountResponse(true);
    }

}

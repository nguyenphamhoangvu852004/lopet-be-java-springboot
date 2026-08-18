package com.nguyenvu.lopet.auth;

import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nguyenvu.lopet.account.entity.Account;
import com.nguyenvu.lopet.account.repository.AccountRepository;
import com.nguyenvu.lopet.auth.dto.LoginRequest;
import com.nguyenvu.lopet.auth.dto.LoginResponse;
import com.nguyenvu.lopet.auth.dto.RefreshTokenRequest;
import com.nguyenvu.lopet.auth.dto.RefreshTokenResponse;
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

    /**
     * Thứ tự kiểm tra giữ nguyên: tồn tại → bị khoá → so khớp mật khẩu. Đổi thứ tự sẽ đổi mã lỗi mà
     * client nhận được trong các tình huống chồng nhau.
     *
     * <p>Tài khoản bị khoá trả 400 (không phải 403) — đó là hành vi hiện tại của backend.
     */
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        Account account = accountRepository.findDetailByUsername(request.username())
                .orElseThrow(() -> new NotFoundException("No user found"));

        if (account.getIsBanned() != null && account.getIsBanned() == 1) {
            throw new BadRequestException("Người dùng " + account.getUsername() + " đã bị khoá");
        }
        if (!passwordEncoder.matches(request.password(), account.getPassword())) {
            throw new BadRequestException("Mật khẩu không trùng khớp");
        }

        UserPrincipal payload = new UserPrincipal(account.getId(), account.getEmail(), rolesOf(account));
        return new LoginResponse(account.getId(),
                jwtService.generateAccessToken(payload),
                jwtService.generateRefreshToken(payload));
    }

    /**
     * Cấp lại cặp token từ refresh token. Đây là đường duy nhất để một phiên sống lâu hơn
     * {@code ACCESS_TOKEN_EXPIRES_IN} (1 giờ) — trước đó access token hết hạn đồng nghĩa người dùng
     * bị đá về màn hình đăng nhập giữa chừng.
     *
     * <p>Ba điểm cố ý:
     *
     * <ul>
     *   <li><b>Đọc lại tài khoản từ DB thay vì tin claim trong token.</b> Roles được ký vào access
     *       token, nên nếu chỉ ký lại payload cũ thì một lần thu hồi quyền phải chờ tới khi refresh
     *       token hết hạn (10 giờ) mới có hiệu lực. Đọc lại DB khiến mỗi lần gia hạn là một lần
     *       đồng bộ quyền.</li>
     *   <li><b>Tài khoản bị khoá thì cắt phiên ngay,</b> và trả 401 chứ không phải 400 như
     *       {@link #login} — 400 chỉ là thông báo cho form đăng nhập, còn ở đây client cần một mã
     *       khiến interceptor xoá phiên và đưa về trang đăng nhập. Đây cũng là cơ chế thu hồi duy
     *       nhất hiện có: ban tài khoản chặn được việc gia hạn, dù access token đang lưu hành vẫn
     *       sống hết phần hạn còn lại của nó.</li>
     *   <li><b>Xoay vòng refresh token.</b> Không lưu trạng thái nên token cũ vẫn dùng được tới khi
     *       hết hạn — chưa phải chống tái sử dụng thật sự, nhưng cho phép client hoạt động liên tục
     *       giữ phiên trượt theo thời gian thay vì bị cắt cứng sau 10 giờ.</li>
     * </ul>
     */
    @Transactional(readOnly = true)
    public RefreshTokenResponse refresh(RefreshTokenRequest request) {
        UserPrincipal claims;
        try {
            claims = jwtService.parseRefreshToken(request.refreshToken());
        } catch (JwtException exception) {
            // Gộp "hết hạn" và "sai chữ ký" về cùng một mã: cả hai đều kết thúc phiên, và phân biệt
            // ra ngoài chỉ giúp người dò token biết mình đoán đúng khoá hay chưa.
            throw new UnauthorizedException(exception.isExpired()
                    ? "Refresh token đã hết hạn"
                    : "Refresh token không hợp lệ");
        }

        if (claims.id() == null) {
            throw new UnauthorizedException("Refresh token không hợp lệ");
        }

        Account account = accountRepository.findDetailById(claims.id())
                .orElseThrow(() -> new UnauthorizedException("Refresh token không hợp lệ"));

        if (account.getIsBanned() != null && account.getIsBanned() == 1) {
            throw new UnauthorizedException("Người dùng " + account.getUsername() + " đã bị khoá");
        }

        UserPrincipal payload = new UserPrincipal(account.getId(), account.getEmail(), rolesOf(account));
        return new RefreshTokenResponse(account.getId(),
                jwtService.generateAccessToken(payload),
                jwtService.generateRefreshToken(payload));
    }

    /**
     * Đăng ký bắt buộc đã qua OTP. Cờ được XOÁ ngay trước khi kiểm tra trùng email/username — giữ
     * nguyên thứ tự của bản TS: đăng ký trùng thì cờ đã mất và người dùng phải xin OTP lại.
     */
    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String verified = otpStore.findVerifiedFlag(request.email());
        if (verified == null) {
            throw new BadRequestException("Bạn cần xác thực OTP trước khi đăng ký.");
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

        // Hồ sơ được cấp ngay tại đây thay vì để người dùng tự tạo bằng request riêng. Quan hệ
        // Account.profile khai cascade PERSIST nên JPA insert `profiles` rồi set `accounts.profileId`
        // trong cùng transaction — đăng ký hỏng thì không để lại hồ sơ mồ côi.
        Account saved = accountRepository.save(Account.builder()
                .email(request.email())
                .username(request.username())
                .password(passwordEncoder.encode(request.password()))
                .isBanned(0)
                .accountProfile(AccountProfileFactory.seedFor(request.username()))
                .build());

        return new RegisterResponse(saved.getId(), saved.getEmail(), saved.getUsername());
    }

    /**
     * Đổi mật khẩu qua email — luồng KHÔNG cần đăng nhập, nên bắt buộc phải có bằng chứng người gọi
     * kiểm soát được hòm thư của tài khoản đó. Thiếu bước này thì bất kỳ ai biết địa chỉ email đều
     * chiếm được tài khoản tương ứng, kể cả tài khoản ADMIN được seed sẵn.
     *
     * <p>Cờ được tiêu thụ TRƯỚC khi ghi mật khẩu để luồng này luôn fail-closed.
     */
    @Transactional
    public ResetPasswordResponse resetPassword(ResetPasswordRequest request) {
        if (!request.password().equals(request.confirmPassword())) {
            throw new BadRequestException("Mật khẩu xác nhận không khớp");
        }

        String verified = otpStore.consumeVerifiedFlag(request.email());
        if (verified == null) {
            throw new ForbiddenException("Bạn cần xác thực OTP trước khi đổi mật khẩu.");
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
                .orElseThrow(() -> new NotFoundException("Không tim thấy tài khoản"));

        if (!passwordEncoder.matches(request.password(), account.getPassword())) {
            throw new BadRequestException("Sai mật khẩu");
        }
        return new VerifyAccountResponse(true);
    }

    private List<String> rolesOf(Account account) {
        return account.getAccountRoles().stream()
                .map(accountRole -> accountRole.getRole().getName().name())
                .toList();
    }
}

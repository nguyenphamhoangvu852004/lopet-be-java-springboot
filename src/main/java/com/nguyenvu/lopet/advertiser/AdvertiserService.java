package com.nguyenvu.lopet.advertiser;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nguyenvu.lopet.account.entity.Account;
import com.nguyenvu.lopet.account.repository.AccountRepository;
import com.nguyenvu.lopet.advertiser.dto.AdvertiserDtos;
import com.nguyenvu.lopet.advertiser.entity.AdvertiserProfile;
import com.nguyenvu.lopet.advertiser.entity.AdvertiserStatus;
import com.nguyenvu.lopet.advertiser.repository.AdvertiserProfileRepository;
import com.nguyenvu.lopet.common.exception.BadRequestException;
import com.nguyenvu.lopet.common.exception.ConflictException;
import com.nguyenvu.lopet.common.exception.ForbiddenException;
import com.nguyenvu.lopet.common.exception.NotFoundException;

import lombok.RequiredArgsConstructor;

/**
 * Vòng đời tư cách nhà quảng cáo — thứ thay thế cho role 'ADS' cũ:
 * đăng ký → PENDING → (staff duyệt) → APPROVED → (vi phạm) → SUSPENDED.
 */
@Service
@RequiredArgsConstructor
public class AdvertiserService {

    private final AdvertiserProfileRepository advertiserRepository;
    private final AccountRepository accountRepository;

    /** Luôn tạo ở PENDING — tự đăng ký không đồng nghĩa được chạy quảng cáo */
    @Transactional
    public AdvertiserDtos.AdvertiserProfileResponse register(Integer accountId, String companyName) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy tài khoản"));

        if (advertiserRepository.findByAccountId(accountId).isPresent()) {
            throw new ConflictException("Tài khoản đã có hồ sơ nhà quảng cáo");
        }
        if (companyName == null || companyName.isEmpty()) {
            throw new BadRequestException("Thiếu companyName");
        }

        AdvertiserProfile created = advertiserRepository.save(AdvertiserProfile.builder()
                .account(account)
                .companyName(companyName)
                .status(AdvertiserStatus.PENDING)
                .balance(BigDecimal.ZERO)
                .build());

        return toDto(created);
    }

    @Transactional(readOnly = true)
    public AdvertiserDtos.AdvertiserProfileResponse getMine(Integer accountId) {
        return toDto(advertiserRepository.findByAccountId(accountId)
                .orElseThrow(() -> new NotFoundException("Bạn chưa có hồ sơ nhà quảng cáo")));
    }

    @Transactional(readOnly = true)
    public List<AdvertiserDtos.AdvertiserProfileResponse> getList() {
        return advertiserRepository.findAllDetail().stream().map(this::toDto).toList();
    }

    @Transactional
    public AdvertiserDtos.AdvertiserProfileResponse review(Integer profileId, String rawStatus, Integer reviewerId) {
        AdvertiserProfile profile = advertiserRepository.findDetailById(profileId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy hồ sơ nhà quảng cáo"));

        AdvertiserStatus status;
        try {
            status = AdvertiserStatus.valueOf(rawStatus);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BadRequestException("Trạng thái không hợp lệ: " + rawStatus);
        }

        profile.setStatus(status);
        // Cột audit: staff nào đã ra quyết định, vào lúc nào
        profile.setApprovedBy(accountRepository.getReferenceById(reviewerId));
        profile.setApprovedAt(LocalDateTime.now());

        advertiserRepository.save(profile);
        return toDto(advertiserRepository.findDetailById(profileId).orElse(profile));
    }

    /**
     * Tầng CAPABILITY: "tài khoản này có tư cách chạy quảng cáo không?" — thay cho câu hỏi cũ "có
     * role ADS không", vì role không mang được trạng thái duyệt.
     *
     * <p>Phải được gọi TRƯỚC khi upload ảnh: nếu chặn muộn thì một tài khoản PENDING/SUSPENDED vẫn
     * kịp tốn một lượt upload lên Cloudinary rồi mới bị từ chối.
     */
    @Transactional(readOnly = true)
    public AdvertiserProfile requireApproved(Integer accountId) {
        AdvertiserProfile profile = advertiserRepository.findByAccountId(accountId)
                .orElseThrow(() -> new ForbiddenException("Tài khoản chưa đăng ký hồ sơ nhà quảng cáo"));

        if (profile.getStatus() != AdvertiserStatus.APPROVED) {
            throw new ForbiddenException("Hồ sơ nhà quảng cáo đang ở trạng thái " + profile.getStatus());
        }
        return profile;
    }

    private AdvertiserDtos.AdvertiserProfileResponse toDto(AdvertiserProfile profile) {
        return new AdvertiserDtos.AdvertiserProfileResponse(
                profile.getId(),
                profile.getAccount() == null ? null : profile.getAccount().getId(),
                profile.getAccount() == null ? null : profile.getAccount().getUsername(),
                profile.getCompanyName(),
                profile.getStatus(),
                profile.getBalance() == null ? null : profile.getBalance().doubleValue(),
                profile.getDailyLimit() == null ? null : profile.getDailyLimit().doubleValue(),
                profile.getApprovedBy() == null ? null : profile.getApprovedBy().getId(),
                profile.getApprovedAt(),
                profile.getCreatedAt());
    }
}

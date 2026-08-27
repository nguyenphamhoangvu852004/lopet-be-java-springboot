package com.nguyenvu.lopet.accountprofile;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nguyenvu.lopet.account.AccountMapper;
import com.nguyenvu.lopet.account.entity.Account;
import com.nguyenvu.lopet.common.exception.BadRequestException;
import com.nguyenvu.lopet.common.exception.NotFoundException;
import com.nguyenvu.lopet.accountprofile.dto.AccountProfileDtos;
import com.nguyenvu.lopet.accountprofile.entity.AccountProfile;
import com.nguyenvu.lopet.accountprofile.entity.ProfileVisibility;
import com.nguyenvu.lopet.accountprofile.repository.AccountProfileRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AccountProfileService {

    private final AccountProfileRepository accountProfileRepository;

    @Transactional(readOnly = true)
    public AccountProfileDtos.ProfileSummary findByAccountId(Integer accountId) {
        return toSummary(accountProfileRepository.findByAccountId(accountId).orElseThrow(NotFoundException::new));
    }

    /**
     * Hồ sơ của người khác. Trả 404 cả khi hồ sơ tồn tại nhưng bị ẩn: phản hồi phải giống hệt
     * trường hợp không có tài khoản nào mang id đó, nếu không endpoint này thành công cụ dò.
     */
    @Transactional(readOnly = true)
    public AccountProfileDtos.PublicProfile findVisibleByAccountId(Integer accountId, Integer viewerId) {
        AccountProfile profile = accountProfileRepository.findVisibleByAccountId(accountId, viewerId)
                .orElseThrow(NotFoundException::new);
        Account account = profile.getAccount();
        return new AccountProfileDtos.PublicProfile(profile.getId(),
                account == null ? null : account.getId(),
                account == null ? null : account.getUsername(),
                profile.getFullName(), profile.getBio(), profile.getAvatarUrl(), profile.getCoverUrl());
    }

    /**
     * Cập nhật hồ sơ CỦA CHÍNH NGƯỜI GỌI. Hồ sơ được tra bằng {@code accountId} lấy từ token chứ
     * không phải {@code profileId} trên URL — đó là lý do module này không còn cần tầng ownership:
     * không có tham số nào để trỏ sang hồ sơ của người khác.
     *
     * <p>Ngữ nghĩa MERGE: {@code null} giữ nguyên trường cũ, mọi giá trị khác (kể cả chuỗi rỗng)
     * thì ghi đè. Chuỗi rỗng vẫn ghi đè là có chủ ý — người dùng phải xoá được bio của mình.
     *
     * <p>Khác với bản cũ: controller giờ truyền {@code null} cho {@code avatarUrl}/{@code coverUrl}
     * khi request không đính file, thay vì chuỗi rỗng. Bản cũ luôn truyền chuỗi rỗng nên mỗi lần
     * sửa bio không kèm ảnh là XOÁ luôn avatar — đó là bug, không phải tính năng.
     */
    @Transactional
    public AccountProfileDtos.ProfileEntity updateMine(Integer accountId, String fullName, String phoneNumber,
                                                 String bio, String avatarUrl, String coverUrl,
                                                 LocalDate dateOfBirth, String hometown, Integer sex,
                                                 String visibility) {
        // Không tìm thấy nghĩa là tài khoản có từ trước refactor và chưa được backfill — xem
        // scripts/backfill-account-profiles.sql. Tài khoản tạo mới luôn có hồ sơ ngay từ đầu.
        AccountProfile profile = accountProfileRepository.findByAccountId(accountId)
                .orElseThrow(() -> new NotFoundException(
                        "Tài khoản chưa có hồ sơ — cần chạy backfill trong scripts/backfill-account-profiles.sql"));

        if (fullName != null) {
            profile.setFullName(fullName);
        }
        if (phoneNumber != null) {
            profile.setPhoneNumber(phoneNumber);
        }
        if (bio != null) {
            profile.setBio(bio);
        }
        if (avatarUrl != null) {
            profile.setAvatarUrl(avatarUrl);
        }
        if (coverUrl != null) {
            profile.setCoverUrl(coverUrl);
        }
        if (sex != null) {
            profile.setSex(sex);
        }
        if (dateOfBirth != null) {
            profile.setDateOfBirth(dateOfBirth);
        }
        if (hometown != null) {
            profile.setHometown(hometown);
        }
        // parseVisibility trả null cho chuỗi rỗng, và cột là NOT NULL — gán thẳng kết quả sẽ ghi NULL
        // vào đó. Form multipart gửi ô không điền dưới dạng chuỗi rỗng nên nhánh này chạy thật.
        ProfileVisibility parsedVisibility = visibility == null ? null : parseVisibility(visibility);
        if (parsedVisibility != null) {
            profile.setVisibility(parsedVisibility);
        }
        profile.setUpdatedAt(LocalDateTime.now());

        return toEntityView(accountProfileRepository.save(profile));
    }

    /**
     * Chuỗi rỗng bị coi là thiếu giá trị chứ không phải lỗi — khớp cách các trường khác của endpoint
     * này xử lý form multipart, nơi một ô không điền vẫn được gửi lên dưới dạng chuỗi rỗng.
     */
    private ProfileVisibility parseVisibility(String raw) {
        if (raw.isEmpty()) {
            return null;
        }
        try {
            return ProfileVisibility.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("visibility phải là PUBLIC, FRIEND hoặc PRIVATE");
        }
    }

    static AccountProfileDtos.ProfileSummary toSummary(AccountProfile profile) {
        return new AccountProfileDtos.ProfileSummary(profile.getId(), profile.getFullName(), profile.getPhoneNumber(),
                profile.getBio(), profile.getAvatarUrl(), profile.getCoverUrl(), profile.getDateOfBirth(),
                profile.getHometown(), profile.getSex(), profile.getVisibility());
    }

    static AccountProfileDtos.ProfileEntity toEntityView(AccountProfile profile) {
        return new AccountProfileDtos.ProfileEntity(profile.getCreatedAt(), profile.getUpdatedAt(),
                profile.getDeletedAt(), profile.getId(), profile.getFullName(), profile.getPhoneNumber(),
                profile.getBio(), profile.getSex(), profile.getDateOfBirth(), profile.getHometown(),
                profile.getAvatarUrl(), profile.getCoverUrl(), profile.getVisibility(),
                AccountMapper.toBrief(profile.getAccount()));
    }
}

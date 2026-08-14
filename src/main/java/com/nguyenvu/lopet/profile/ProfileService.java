package com.nguyenvu.lopet.profile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nguyenvu.lopet.account.AccountMapper;
import com.nguyenvu.lopet.common.exception.NotFoundException;
import com.nguyenvu.lopet.profile.dto.ProfileDtos;
import com.nguyenvu.lopet.profile.entity.Profile;
import com.nguyenvu.lopet.profile.repository.ProfileRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProfileService {

    private final ProfileRepository profileRepository;

    @Transactional(readOnly = true)
    public List<ProfileDtos.ProfileSummary> findAll(Integer id, String fullName) {
        return profileRepository.search(id, fullName).stream()
                .map(ProfileService::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProfileDtos.ProfileEntity findById(Integer id) {
        return toEntityView(profileRepository.findDetailById(id).orElseThrow(NotFoundException::new));
    }

    @Transactional(readOnly = true)
    public ProfileDtos.ProfileSummary findByAccountId(Integer accountId) {
        return toSummary(profileRepository.findByAccountId(accountId).orElseThrow(NotFoundException::new));
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
    public ProfileDtos.ProfileEntity updateMine(Integer accountId, String fullName, String phoneNumber,
                                                 String bio, String avatarUrl, String coverUrl,
                                                 LocalDate dateOfBirth, String hometown, Integer sex) {
        // Không tìm thấy nghĩa là tài khoản có từ trước refactor và chưa được backfill — xem
        // scripts/profile-refactor-migration.sql. Tài khoản tạo mới luôn có hồ sơ ngay từ đầu.
        Profile profile = profileRepository.findByAccountId(accountId)
                .orElseThrow(() -> new NotFoundException(
                        "Tài khoản chưa có hồ sơ — cần chạy backfill trong scripts/profile-refactor-migration.sql"));

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
        profile.setUpdatedAt(LocalDateTime.now());

        return toEntityView(profileRepository.save(profile));
    }

    static ProfileDtos.ProfileSummary toSummary(Profile profile) {
        return new ProfileDtos.ProfileSummary(profile.getId(), profile.getFullName(), profile.getPhoneNumber(),
                profile.getBio(), profile.getAvatarUrl(), profile.getCoverUrl(), profile.getDateOfBirth(),
                profile.getHometown(), profile.getSex());
    }

    static ProfileDtos.ProfileEntity toEntityView(Profile profile) {
        return new ProfileDtos.ProfileEntity(profile.getCreatedAt(), profile.getUpdatedAt(),
                profile.getDeletedAt(), profile.getId(), profile.getFullName(), profile.getPhoneNumber(),
                profile.getBio(), profile.getSex(), profile.getDateOfBirth(), profile.getHometown(),
                profile.getAvatarUrl(), profile.getCoverUrl(), AccountMapper.toBrief(profile.getAccount()));
    }
}

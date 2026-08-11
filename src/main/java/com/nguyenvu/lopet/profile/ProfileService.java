package com.nguyenvu.lopet.profile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nguyenvu.lopet.account.AccountMapper;
import com.nguyenvu.lopet.account.entity.Account;
import com.nguyenvu.lopet.account.repository.AccountRepository;
import com.nguyenvu.lopet.common.exception.BadRequestException;
import com.nguyenvu.lopet.common.exception.NotFoundException;
import com.nguyenvu.lopet.profile.dto.ProfileDtos;
import com.nguyenvu.lopet.profile.entity.Profile;
import com.nguyenvu.lopet.profile.repository.ProfileRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProfileService {

    private final ProfileRepository profileRepository;
    private final AccountRepository accountRepository;

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
     * Tạo hồ sơ RỜI, chưa gắn vào tài khoản nào — việc gắn là bước riêng qua
     * {@link #setToAccount(Integer, Integer)}. Giữ nguyên mô hình hai bước của backend cũ.
     */
    @Transactional
    public ProfileDtos.ProfileSummary create(String fullName, String phoneNumber, String bio,
                                              LocalDate dateOfBirth, String hometown, Integer sex,
                                              String avatarUrl, String coverUrl) {
        Profile profile = profileRepository.save(Profile.builder()
                .fullName(orEmpty(fullName))
                .phoneNumber(orEmpty(phoneNumber))
                .bio(orEmpty(bio))
                .avatarUrl(orEmpty(avatarUrl))
                .coverUrl(orEmpty(coverUrl))
                .dateOfBirth(dateOfBirth)
                .hometown(hometown)
                .sex(sex)
                .build());
        return toSummary(profile);
    }

    /**
     * Cập nhật hồ sơ. Giá trị {@code null} giữ nguyên trường cũ, nhưng CHUỖI RỖNG thì ghi đè —
     * bản TS dùng {@code data.x ?? profile.x}, mà {@code ??} không bắt chuỗi rỗng. Hệ quả có thật:
     * PATCH không kèm file ảnh sẽ XOÁ avatarUrl/coverUrl, vì controller cũ luôn truyền chuỗi rỗng
     * cho hai trường đó. Hành vi này được giữ nguyên và ghi lại ở MIGRATION_FINAL_REPORT.
     */
    @Transactional
    public ProfileDtos.ProfileEntity update(Integer id, String fullName, String phoneNumber, String bio,
                                             String avatarUrl, String coverUrl, LocalDate dateOfBirth,
                                             String hometown, Integer sex) {
        Profile profile = profileRepository.findDetailById(id).orElseThrow(BadRequestException::new);

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

    /** accountId lấy từ token, không nhận từ body — nếu không thì gắn được hồ sơ vào tài khoản người khác */
    @Transactional
    public ProfileDtos.ProfileShort setToAccount(Integer profileId, Integer accountId) {
        Profile profile = profileRepository.findDetailById(profileId).orElseThrow(BadRequestException::new);
        Account account = accountRepository.findById(accountId).orElseThrow(BadRequestException::new);

        account.setProfile(profile);
        accountRepository.save(account);

        return new ProfileDtos.ProfileShort(profile.getId(), profile.getFullName(), profile.getPhoneNumber(),
                profile.getBio(), profile.getAvatarUrl(), profile.getCoverUrl());
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

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }
}

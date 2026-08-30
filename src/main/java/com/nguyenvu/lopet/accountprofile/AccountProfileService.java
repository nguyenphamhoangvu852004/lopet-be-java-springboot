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

    @Transactional
    public AccountProfileDtos.ProfileEntity updateMine(Integer accountId, String fullName, String phoneNumber,
                                                 String bio, String avatarUrl, String coverUrl,
                                                 LocalDate dateOfBirth, String hometown, Integer sex,
                                                 String visibility) {
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
        ProfileVisibility parsedVisibility = visibility == null ? null : parseVisibility(visibility);
        if (parsedVisibility != null) {
            profile.setVisibility(parsedVisibility);
        }
        profile.setUpdatedAt(LocalDateTime.now());

        return toEntityView(accountProfileRepository.save(profile));
    }

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

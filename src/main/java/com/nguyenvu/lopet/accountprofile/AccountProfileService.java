package com.nguyenvu.lopet.accountprofile;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.nguyenvu.lopet.account.AccountMapper;
import com.nguyenvu.lopet.account.entity.Account;
import com.nguyenvu.lopet.common.exception.NotFoundException;
import com.nguyenvu.lopet.accountprofile.dto.AccountProfileDtos;
import com.nguyenvu.lopet.accountprofile.entity.AccountProfile;
import com.nguyenvu.lopet.accountprofile.repository.AccountProfileRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AccountProfileService {

    private final AccountProfileRepository accountProfileRepository;
    private final AccountProfileCache accountProfileCache;

    @Transactional(readOnly = true)
    public AccountProfileDtos.ProfileSummary findByAccountId(Integer accountId) {
        AccountProfileDtos.ProfileSummary cached = accountProfileCache.findSummary(accountId);
        if (cached != null) {
            return cached;
        }

        AccountProfileDtos.ProfileSummary summary = toSummary(
                accountProfileRepository.findByAccountId(accountId).orElseThrow(NotFoundException::new));
        accountProfileCache.saveSummary(accountId, summary);
        return summary;
    }

    @Transactional(readOnly = true)
    public AccountProfileDtos.PublicProfile findPublicByAccountId(Integer accountId) {
        AccountProfileDtos.PublicProfile cached = accountProfileCache.findPublic(accountId);
        if (cached != null) {
            return cached;
        }

        AccountProfile profile = accountProfileRepository.findWithAccountByAccountId(accountId)
                .orElseThrow(NotFoundException::new);
        Account account = profile.getAccount();
        AccountProfileDtos.PublicProfile publicProfile = new AccountProfileDtos.PublicProfile(profile.getId(),
                account == null ? null : account.getId(),
                account == null ? null : account.getUsername(),
                profile.getFullName(), profile.getBio(), profile.getAvatarUrl(), profile.getCoverUrl());
        accountProfileCache.savePublic(accountId, publicProfile);
        return publicProfile;
    }

    @Transactional
    public AccountProfileDtos.ProfileEntity updateMine(Integer accountId, String fullName, String phoneNumber,
                                                 String bio, String avatarUrl, String coverUrl,
                                                 LocalDate dateOfBirth, String hometown, Integer sex) {
        AccountProfile profile = accountProfileRepository.findByAccountId(accountId)
                .orElseThrow(() -> new NotFoundException(
                        "Account has no profile yet — a profile backfill is required"));

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

        AccountProfileDtos.ProfileEntity updated = toEntityView(accountProfileRepository.save(profile));
        evictAfterCommit(accountId);
        return updated;
    }

    private void evictAfterCommit(Integer accountId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            accountProfileCache.evict(accountId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                accountProfileCache.evict(accountId);
            }
        });
    }

    static AccountProfileDtos.ProfileSummary toSummary(AccountProfile profile) {
        return new AccountProfileDtos.ProfileSummary(profile.getId(), profile.getFullName(), profile.getPhoneNumber(),
                profile.getBio(), profile.getAvatarUrl(), profile.getCoverUrl(), profile.getDateOfBirth(),
                profile.getHometown(), profile.getSex());
    }

    static AccountProfileDtos.ProfileEntity toEntityView(AccountProfile profile) {
        return new AccountProfileDtos.ProfileEntity(profile.getCreatedAt(), profile.getUpdatedAt(),
                profile.getDeletedAt(), profile.getId(), profile.getFullName(), profile.getPhoneNumber(),
                profile.getBio(), profile.getSex(), profile.getDateOfBirth(), profile.getHometown(),
                profile.getAvatarUrl(), profile.getCoverUrl(),
                AccountMapper.toBrief(profile.getAccount()));
    }
}

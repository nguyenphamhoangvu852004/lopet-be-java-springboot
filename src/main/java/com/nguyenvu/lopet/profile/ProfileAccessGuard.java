package com.nguyenvu.lopet.profile;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.nguyenvu.lopet.profile.entity.Profile;
import com.nguyenvu.lopet.profile.repository.ProfileRepository;
import com.nguyenvu.lopet.security.authz.OwnershipGuard;

import lombok.RequiredArgsConstructor;

/**
 * Hồ sơ cá nhân là dữ liệu riêng: KHÔNG cho ADMIN bỏ qua ownership. Kiểm duyệt viên có quyền khoá
 * tài khoản, không có việc sửa hộ hồ sơ.
 */
@Component
@RequiredArgsConstructor
public class ProfileAccessGuard {

    private final ProfileRepository profileRepository;

    @Transactional(readOnly = true)
    public void requireOwner(Integer profileId) {
        OwnershipGuard.check(
                () -> profileRepository.findDetailById(profileId).orElse(null),
                (Profile profile) -> OwnershipGuard.owners(
                        profile.getAccount() == null ? null : profile.getAccount().getId()),
                OwnershipGuard.NO_BYPASS);
    }
}

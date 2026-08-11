package com.nguyenvu.lopet.advertisement;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.nguyenvu.lopet.advertisement.entity.Advertisement;
import com.nguyenvu.lopet.advertisement.repository.AdvertisementRepository;
import com.nguyenvu.lopet.security.authz.OwnershipGuard;

import lombok.RequiredArgsConstructor;

/**
 * Chủ sở hữu quảng cáo là {@code advertiser.account.id} — quảng cáo trỏ tới hồ sơ nhà quảng cáo
 * chứ không trỏ thẳng tới tài khoản, nên phải đi qua hai bậc quan hệ.
 *
 * <p>ADMIN được bỏ qua (mặc định), khác với tin nhắn hay hồ sơ cá nhân.
 */
@Component
@RequiredArgsConstructor
public class AdvertisementAccessGuard {

    private final AdvertisementRepository advertisementRepository;

    @Transactional(readOnly = true)
    public void requireOwner(Integer adsId) {
        OwnershipGuard.check(
                () -> advertisementRepository.findDetailById(adsId).orElse(null),
                (Advertisement ads) -> OwnershipGuard.owners(
                        ads.getAdvertiser() == null || ads.getAdvertiser().getAccount() == null
                                ? null
                                : ads.getAdvertiser().getAccount().getId()),
                OwnershipGuard.DEFAULT_BYPASS);
    }
}

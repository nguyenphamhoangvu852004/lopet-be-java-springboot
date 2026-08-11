package com.nguyenvu.lopet.advertisement;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nguyenvu.lopet.advertisement.dto.AdvertisementDtos;
import com.nguyenvu.lopet.advertisement.entity.Advertisement;
import com.nguyenvu.lopet.advertisement.repository.AdvertisementRepository;
import com.nguyenvu.lopet.advertiser.AdvertiserService;
import com.nguyenvu.lopet.advertiser.entity.AdvertiserProfile;
import com.nguyenvu.lopet.common.exception.NotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdvertisementService {

    private final AdvertisementRepository advertisementRepository;
    private final AdvertiserService advertiserService;

    @Transactional(readOnly = true)
    public List<AdvertisementDtos.AdvertisementDetail> getList(Integer accountId) {
        return advertisementRepository.findAllDetail(accountId).stream().map(this::toDetail).toList();
    }

    @Transactional(readOnly = true)
    public AdvertisementDtos.AdvertisementDetail getDetail(Integer id) {
        return toDetail(load(id));
    }

    @Transactional
    public AdvertisementDtos.IdResponse create(Integer accountId, String title, String description,
                                                String linkRef, String imageUrl) {
        AdvertiserProfile profile = advertiserService.requireApproved(accountId);

        Advertisement saved = advertisementRepository.save(Advertisement.builder()
                .advertiser(profile)
                .title(title)
                .description(description)
                .imageUrl(imageUrl)
                .linkReference(linkRef)
                .build());

        return new AdvertisementDtos.IdResponse(saved.getId());
    }

    /**
     * Ownership được kiểm ở guard trên controller, KHÔNG lặp lại ở đây: guard cho ADMIN đi qua, còn
     * kiểm tra tại service lại đòi người thao tác phải có advertiser_profile — admin không có nên sẽ
     * bị chặn ngược. Đặt ownership ở đúng một chỗ để hai tầng không đá nhau.
     */
    @Transactional
    public AdvertisementDtos.IdResponse update(Integer adsId, String title, String description,
                                                String linkRef, String imageUrl) {
        Advertisement ads = load(adsId);

        // Cố tình KHÔNG gán lại advertiser: đổi chủ sở hữu không phải việc của luồng update
        ads.setTitle(title);
        ads.setDescription(description);
        ads.setLinkReference(linkRef);
        if (imageUrl != null) {
            ads.setImageUrl(imageUrl);
        }
        ads.setUpdatedAt(LocalDateTime.now());

        return new AdvertisementDtos.IdResponse(advertisementRepository.save(ads).getId());
    }

    @Transactional
    public AdvertisementDtos.DeleteResponse delete(Integer adsId) {
        advertisementRepository.delete(load(adsId));
        return new AdvertisementDtos.DeleteResponse("Delete success");
    }

    private Advertisement load(Integer adsId) {
        return advertisementRepository.findDetailById(adsId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy quảng cáo"));
    }

    private AdvertisementDtos.AdvertisementDetail toDetail(Advertisement ads) {
        AdvertisementDtos.Author author = new AdvertisementDtos.Author(
                ads.getAdvertiser().getAccount().getId(),
                ads.getAdvertiser().getAccount().getUsername(),
                ads.getAdvertiser().getAccount().getEmail());

        return new AdvertisementDtos.AdvertisementDetail(ads.getId(), author, ads.getTitle(),
                ads.getDescription(), ads.getImageUrl(), ads.getLinkReference(), ads.getCreatedAt(),
                ads.getUpdatedAt(), ads.getDeletedAt());
    }
}

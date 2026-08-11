package com.nguyenvu.lopet.notification;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nguyenvu.lopet.account.entity.Account;
import com.nguyenvu.lopet.account.repository.AccountRepository;
import com.nguyenvu.lopet.common.exception.BadRequestException;
import com.nguyenvu.lopet.common.exception.HttpException;
import com.nguyenvu.lopet.common.exception.NotFoundException;
import com.nguyenvu.lopet.notification.dto.NotificationDtos;
import com.nguyenvu.lopet.notification.entity.Notification;
import com.nguyenvu.lopet.notification.entity.NotificationObjectType;
import com.nguyenvu.lopet.notification.entity.NotificationStatus;
import com.nguyenvu.lopet.notification.repository.NotificationRepository;
import com.nguyenvu.lopet.profile.entity.Profile;
import com.nguyenvu.lopet.profile.repository.ProfileRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final AccountRepository accountRepository;
    private final ProfileRepository profileRepository;

    /**
     * Actor/receptor không tồn tại được ném dưới dạng lỗi 500 kèm nguyên văn message — bản TS dùng
     * {@code new Error(...)} chứ không phải lớp HttpError, nên {@code error.code} là undefined và
     * handler trung tâm rơi về 500.
     */
    @Transactional
    public NotificationDtos.CreateNotificationResponse create(Integer actorId, Integer receptorId, String content,
                                                               String objectType) {
        Account actor = accountRepository.findById(actorId)
                .orElseThrow(() -> new HttpException(500, "Actor with ID " + actorId + " not found"));
        Account receptor = accountRepository.findById(receptorId)
                .orElseThrow(() -> new HttpException(500, "Receptor with ID " + receptorId + " not found"));

        Notification notification = Notification.builder()
                .actor(actor)
                .receptor(receptor)
                .content(content)
                .status(NotificationStatus.SENT)
                .build();

        // Chỉ hai giá trị này được chấp nhận; bất kỳ chuỗi nào khác để cột objectType là null và
        // vi phạm ràng buộc NOT NULL — đúng như bản TS, nơi hai câu if đơn giản không có nhánh else.
        if ("POST".equals(objectType)) {
            notification.setObjectType(NotificationObjectType.POST);
        }
        if ("MESSAGE".equals(objectType)) {
            notification.setObjectType(NotificationObjectType.MESSAGE);
        }

        Notification saved = notificationRepository.save(notification);

        return new NotificationDtos.CreateNotificationResponse(saved.getId(), actor.getId(),
                receptor.getId(), saved.getContent(), saved.getObjectType(), saved.getStatus(),
                saved.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public List<NotificationDtos.NotificationListItem> getList(Integer accountId) {
        return notificationRepository.findByReceptor(accountId).stream()
                .map(notification -> new NotificationDtos.NotificationListItem(
                        notification.getId(),
                        notification.getActor().getId(),
                        notification.getReceptor().getId(),
                        notification.getContent(),
                        notification.getCreatedAt(),
                        notification.getStatus(),
                        notification.getObjectType()))
                .toList();
    }

    @Transactional(readOnly = true)
    public NotificationDtos.NotificationDetail getDetail(Integer id) {
        Notification notification = notificationRepository.findDetailById(id)
                .orElseThrow(NotFoundException::new);

        return new NotificationDtos.NotificationDetail(
                notification.getId(),
                toAccount(notification.getActor()),
                toAccount(notification.getReceptor()),
                notification.getContent(),
                notification.getStatus(),
                notification.getObjectType(),
                notification.getCreatedAt(),
                notification.getUpdatedAt(),
                notification.getDeletedAt());
    }

    @Transactional
    public NotificationDtos.UpdateNotificationResponse updateStatus(Integer id, String rawStatus) {
        Notification notification = notificationRepository.findDetailById(id)
                .orElseThrow(() -> new BadRequestException("Notification with ID " + id + " not found"));

        NotificationStatus status;
        try {
            status = NotificationStatus.valueOf(rawStatus);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BadRequestException("Invalid status: " + rawStatus);
        }
        notification.setStatus(status);

        Notification saved = notificationRepository.save(notification);
        return new NotificationDtos.UpdateNotificationResponse(saved.getId(), saved.getActor().getId(),
                saved.getReceptor().getId(), saved.getContent(), saved.getCreatedAt(), saved.getStatus(),
                saved.getObjectType());
    }

    private NotificationDtos.NotificationAccount toAccount(Account account) {
        Profile profile = profileRepository.findByAccountId(account.getId()).orElse(null);
        NotificationDtos.NotificationProfile profileDto = profile == null
                // Object rỗng {} — không phải null: bản TS gán new GetProfileOutputDTO() chưa set gì
                ? new NotificationDtos.NotificationProfile(null, null, null, null, null, null)
                : new NotificationDtos.NotificationProfile(profile.getId(), profile.getFullName(),
                        profile.getPhoneNumber(), profile.getBio(), profile.getAvatarUrl(),
                        profile.getCoverUrl());

        return new NotificationDtos.NotificationAccount(account.getId(), account.getUsername(),
                account.getEmail(), profileDto);
    }
}

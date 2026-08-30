package com.nguyenvu.lopet.friendship;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nguyenvu.lopet.account.entity.Account;
import com.nguyenvu.lopet.account.repository.AccountRepository;
import com.nguyenvu.lopet.common.exception.BadRequestException;
import com.nguyenvu.lopet.friendship.dto.FriendshipDtos;
import com.nguyenvu.lopet.friendship.entity.Friendship;
import com.nguyenvu.lopet.friendship.entity.FriendshipStatus;
import com.nguyenvu.lopet.friendship.repository.FriendshipRepository;
import com.nguyenvu.lopet.notification.NotificationPublisher;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FriendshipService {

    private final FriendshipRepository friendshipRepository;
    private final AccountRepository accountRepository;
    private final NotificationPublisher notificationPublisher;

    @Transactional(readOnly = true)
    public FriendshipDtos.FriendshipListResponse getSentRequests(Integer accountId) {
        Account account = requireAccount(accountId);
        List<FriendshipDtos.Person> others = friendshipRepository
                .findBySenderAndStatus(accountId, FriendshipStatus.PENDING).stream()
                .map(friendship -> person(friendship.getReceiver(), friendship.getStatus()))
                .toList();
        return new FriendshipDtos.FriendshipListResponse(person(account, null), others);
    }

    @Transactional(readOnly = true)
    public FriendshipDtos.FriendshipListResponse getReceivedRequests(Integer accountId) {
        Account account = requireAccount(accountId);
        List<FriendshipDtos.Person> others = friendshipRepository
                .findByReceiverAndStatus(accountId, FriendshipStatus.PENDING).stream()
                .map(friendship -> person(friendship.getSender(), FriendshipStatus.PENDING))
                .toList();
        return new FriendshipDtos.FriendshipListResponse(person(account, null), others);
    }

    @Transactional(readOnly = true)
    public FriendshipDtos.FriendshipListResponse getFriends(Integer accountId) {
        Account account = requireAccount(accountId);
        List<FriendshipDtos.Person> others = friendshipRepository.findAcceptedOf(accountId).stream()
                .map(friendship -> friendship.getSender().getId().equals(accountId)
                        ? friendship.getReceiver()
                        : friendship.getSender())
                .map(friend -> person(friend, FriendshipStatus.ACCEPTED))
                .toList();
        return new FriendshipDtos.FriendshipListResponse(person(account, null), others);
    }

    @Transactional
    public FriendshipDtos.CreateFriendshipResponse create(Integer senderId, Integer receiverId) {
        Account sender = requireAccount(senderId);
        Account receiver = requireAccount(receiverId);

        if (friendshipRepository.findBySenderAndReceiver(senderId, receiverId).isPresent()) {
            throw new BadRequestException();
        }

        Friendship saved = friendshipRepository.save(Friendship.builder()
                .sender(sender)
                .receiver(receiver)
                .status(FriendshipStatus.PENDING)
                .build());

        notificationPublisher.friendRequested(sender.getId(), receiver.getId());

        return new FriendshipDtos.CreateFriendshipResponse(saved.getId(), sender.getId(), receiver.getId(),
                saved.getCreatedAt());
    }

    @Transactional
    public FriendshipDtos.ChangeStatusResponse changeStatus(Integer senderId, Integer receiverId,
                                                            FriendshipStatus status) {
        requireAccount(senderId);
        requireAccount(receiverId);

        Friendship friendship = friendshipRepository.findBySenderAndReceiver(senderId, receiverId)
                .orElseThrow(BadRequestException::new);
        friendship.setStatus(status);
        Friendship saved = friendshipRepository.save(friendship);

        if (status == FriendshipStatus.ACCEPTED) {
            notificationPublisher.friendAccepted(receiverId, senderId);
        }

        return new FriendshipDtos.ChangeStatusResponse(saved.getSender().getId(),
                saved.getReceiver().getId(), saved.getStatus());
    }

    @Transactional
    public FriendshipDtos.DeleteFriendshipResponse delete(Integer callerId, Integer friendId) {
        requireAccountOrMessage(callerId);
        requireAccountOrMessage(friendId);

        Friendship friendship = friendshipRepository.findBySenderAndReceiver(callerId, friendId)
                .or(() -> friendshipRepository.findBySenderAndReceiver(friendId, callerId))
                .orElseThrow(() -> new BadRequestException("Friendship not found"));

        Integer senderId = friendship.getSender().getId();
        Integer receiverId = friendship.getReceiver().getId();
        friendshipRepository.delete(friendship);

        return new FriendshipDtos.DeleteFriendshipResponse(senderId, receiverId, true);
    }

    @Transactional(readOnly = true)
    public boolean areFriends(Integer accountIdA, Integer accountIdB) {
        if (accountIdA.equals(accountIdB)) {
            return true;
        }
        return friendshipRepository.countAcceptedBetween(accountIdA, accountIdB) > 0;
    }

    private Account requireAccount(Integer id) {
        return accountRepository.findDetailById(id).orElseThrow(BadRequestException::new);
    }

    private void requireAccountOrMessage(Integer id) {
        accountRepository.findById(id)
                .orElseThrow(() -> new BadRequestException("Sender or receiver not found"));
    }

    private FriendshipDtos.Person person(Account account, FriendshipStatus status) {
        String avatarUrl = account.getAccountProfile() == null ? "" : account.getAccountProfile().getAvatarUrl();
        return new FriendshipDtos.Person(account.getId(), account.getUsername(),
                avatarUrl == null ? "" : avatarUrl, status);
    }
}

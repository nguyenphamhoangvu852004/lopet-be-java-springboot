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

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FriendshipService {

    private final FriendshipRepository friendshipRepository;
    private final AccountRepository accountRepository;

    /** Lời mời PENDING mà người gọi ĐÃ GỬI đi */
    @Transactional(readOnly = true)
    public FriendshipDtos.FriendshipListResponse getSentRequests(Integer accountId) {
        Account account = requireAccount(accountId);
        List<FriendshipDtos.Person> others = friendshipRepository
                .findBySenderAndStatus(accountId, FriendshipStatus.PENDING).stream()
                .map(friendship -> person(friendship.getReceiver(), friendship.getStatus()))
                .toList();
        return new FriendshipDtos.FriendshipListResponse(person(account, null), others);
    }

    /** Lời mời PENDING mà người gọi NHẬN được */
    @Transactional(readOnly = true)
    public FriendshipDtos.FriendshipListResponse getReceivedRequests(Integer accountId) {
        Account account = requireAccount(accountId);
        List<FriendshipDtos.Person> others = friendshipRepository
                .findByReceiverAndStatus(accountId, FriendshipStatus.PENDING).stream()
                .map(friendship -> person(friendship.getSender(), FriendshipStatus.PENDING))
                .toList();
        return new FriendshipDtos.FriendshipListResponse(person(account, null), others);
    }

    /** Bạn bè ACCEPTED của một tài khoản, xét cả hai chiều gửi/nhận */
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

    /**
     * Chỉ chặn trùng ĐÚNG CHIỀU gửi→nhận, giống bản TS: nếu B đã gửi cho A thì A vẫn gửi được cho B
     * và bảng có hai bản ghi ngược chiều. Không "sửa" thành dò hai chiều ở đây — đó là đổi nghiệp vụ.
     */
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

        return new FriendshipDtos.CreateFriendshipResponse(saved.getId(), sender.getId(), receiver.getId(),
                saved.getCreatedAt());
    }

    /**
     * Đổi trạng thái lời mời. {@code receiverId} luôn là người gọi (lấy từ token), nên chỉ người
     * NHẬN mới chấp nhận/từ chối được lời mời gửi cho mình.
     */
    @Transactional
    public FriendshipDtos.ChangeStatusResponse changeStatus(Integer senderId, Integer receiverId,
                                                            FriendshipStatus status) {
        requireAccount(senderId);
        requireAccount(receiverId);

        Friendship friendship = friendshipRepository.findBySenderAndReceiver(senderId, receiverId)
                .orElseThrow(BadRequestException::new);
        friendship.setStatus(status);
        Friendship saved = friendshipRepository.save(friendship);

        return new FriendshipDtos.ChangeStatusResponse(saved.getSender().getId(),
                saved.getReceiver().getId(), saved.getStatus());
    }

    /**
     * Huỷ kết bạn. Một đầu LUÔN là chính người gọi — trước bản vá, endpoint nhận cả senderId lẫn
     * receiverId từ body và không đối chiếu người gọi, nên bất kỳ ai đăng nhập cũng huỷ được quan hệ
     * bạn bè giữa hai người xa lạ.
     */
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

    /** Hai tài khoản đã là bạn bè hay chưa — dùng bởi guard bảo vệ danh sách bạn bè */
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
        String avatarUrl = account.getProfile() == null ? "" : account.getProfile().getAvatarUrl();
        return new FriendshipDtos.Person(account.getId(), account.getUsername(),
                avatarUrl == null ? "" : avatarUrl, status);
    }
}

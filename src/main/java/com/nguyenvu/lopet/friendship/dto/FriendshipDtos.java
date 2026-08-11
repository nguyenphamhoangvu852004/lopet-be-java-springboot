package com.nguyenvu.lopet.friendship.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.nguyenvu.lopet.friendship.entity.FriendshipStatus;

public final class FriendshipDtos {

    /**
     * {@code me} là tài khoản ĐANG ĐƯỢC XEM (theo {@code :id}), không phải người gọi — và cũng
     * không có {@code status} vì bản TS không gán trường đó cho phần tử này.
     *
     * <p>Không DTO nào ở đây chứa email: chúng liệt kê nhiều tài khoản một lúc, để email vào là rò
     * email hàng loạt cho bất kỳ ai gọi được endpoint.
     */
    public record FriendshipListResponse(Person me, List<Person> others) {
    }

    public record Person(
            Integer id,
            String username,
            String imageUrl,
            @JsonInclude(JsonInclude.Include.NON_NULL) FriendshipStatus status) {
    }

    public record CreateFriendshipRequest(Integer receiverId) {
    }

    public record CreateFriendshipResponse(Integer id, Integer senderId, Integer receiverId, LocalDateTime createdAt) {
    }

    public record ChangeStatusRequest(Integer senderId) {
    }

    public record ChangeStatusResponse(Integer senderId, Integer receiverId, FriendshipStatus status) {
    }

    public record DeleteFriendshipRequest(Integer friendId) {
    }

    public record DeleteFriendshipResponse(Integer senderId, Integer receiverId, boolean isSuccess) {
    }

    private FriendshipDtos() {
    }
}

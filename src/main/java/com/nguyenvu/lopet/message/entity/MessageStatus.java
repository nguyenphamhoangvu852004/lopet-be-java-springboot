package com.nguyenvu.lopet.message.entity;

/**
 * Trạng thái giao nhận của một tin nhắn. Thứ tự khai báo CHÍNH LÀ thứ tự tiến của vòng đời —
 * {@link #isAtLeast(MessageStatus)} dựa vào {@code ordinal()}, nên đảo vị trí hai hằng số ở đây là
 * đảo luôn luật "không hạ cấp". Thứ tự này cũng khớp với {@code enum('SENT','DELIVERED','READ')}
 * của cột trong MySQL.
 *
 * <p>Trạng thái chỉ đi một chiều SENT → DELIVERED → READ. Không có đường lùi: một tin đã được đọc
 * thì không thể "chưa nhận" lại được, và nếu cho phép lùi thì client chỉ cần gửi một request là xoá
 * được dấu đã xem của mình.
 */
public enum MessageStatus {
    SENT,
    DELIVERED,
    READ;

    /** {@code true} khi trạng thái này đã bằng hoặc vượt {@code other} trong vòng đời */
    public boolean isAtLeast(MessageStatus other) {
        return ordinal() >= other.ordinal();
    }
}

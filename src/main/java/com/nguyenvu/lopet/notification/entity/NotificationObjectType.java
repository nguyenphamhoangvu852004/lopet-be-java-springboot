package com.nguyenvu.lopet.notification.entity;

/**
 * Loại thông báo — quyết định biểu tượng, câu chữ và NƠI GIAO DIỆN ĐIỀU HƯỚNG TỚI khi người dùng
 * bấm vào. Đây là hợp đồng với frontend, không phải nhãn trang trí: mỗi hằng số dưới đây ứng với
 * một đích đến cụ thể, và {@code objectId} của thông báo là tham số của đích đó.
 *
 * <p>Bảng đích đến (frontend: components/layout/NotificationBell.tsx):
 * <table>
 *   <tr><th>Loại</th><th>objectId là gì</th><th>Bấm vào thì đi đâu</th></tr>
 *   <tr><td>{@link #POST_LIKE}</td><td>id bài viết</td><td>/posts/{objectId}</td></tr>
 *   <tr><td>{@link #POST_COMMENT}</td><td>id bài viết</td><td>/posts/{objectId}</td></tr>
 *   <tr><td>{@link #MESSAGE}</td><td>id tin nhắn</td><td>/messages/{actorId}</td></tr>
 *   <tr><td>{@link #FRIEND_REQUEST}</td><td>id người gửi</td><td>/friends</td></tr>
 *   <tr><td>{@link #FRIEND_ACCEPTED}</td><td>id người chấp nhận</td><td>/profile/{actorId}</td></tr>
 *   <tr><td>{@link #GROUP_JOIN_REQUESTED}</td><td>id nhóm</td><td>/groups/{objectId}/requests</td></tr>
 *   <tr><td>{@link #GROUP_JOIN_APPROVED}</td><td>id nhóm</td><td>/groups/{objectId}</td></tr>
 *   <tr><td>{@link #GROUP_INVITED}</td><td>id nhóm</td><td>/groups/{objectId}</td></tr>
 *   <tr><td>{@link #GROUP_INVITE_ACCEPTED}</td><td>id nhóm</td><td>/groups/{objectId}</td></tr>
 *   <tr><td>{@link #POST}</td><td>không có</td><td>không đi đâu cả</td></tr>
 * </table>
 *
 * <p>Thêm một hằng số ở đây là phải làm ba việc: nới enum của cột {@code notifications.objectType}
 * trong MySQL, khai báo câu chữ ở {@code NotificationPublisher}, và thêm đích đến ở frontend.
 * Thiếu bước một thì mọi lần ghi đều chết vì vi phạm ràng buộc cột.
 */
public enum NotificationObjectType {

    /** Ai đó thích bài viết của người nhận */
    POST_LIKE,

    /** Ai đó bình luận bài viết của người nhận */
    POST_COMMENT,

    /** Ai đó gửi tin nhắn cho người nhận */
    MESSAGE,

    /** Ai đó gửi lời mời kết bạn tới người nhận */
    FRIEND_REQUEST,

    /** Ai đó chấp nhận lời mời kết bạn của người nhận */
    FRIEND_ACCEPTED,

    /**
     * Một thú cưng xin vào nhóm PRIVATE — người nhận là quản trị nhóm (OWNER/ADMIN).
     *
     * <p>{@code objectId} là id NHÓM, không phải id pet xin vào: đích đến là hộp thư yêu cầu của
     * nhóm, và pet nào xin thì đọc từ {@code actorId}. Nhóm PUBLIC không sinh loại này vì vào nhóm
     * PUBLIC không cần ai duyệt.
     */
    GROUP_JOIN_REQUESTED,

    /** Yêu cầu vào nhóm được duyệt — người nhận là chủ của pet đã xin vào */
    GROUP_JOIN_APPROVED,

    /** Ai đó mời thú cưng của người nhận vào nhóm; chờ chính người nhận trả lời */
    GROUP_INVITED,

    /** Lời mời vào nhóm được chấp nhận — người nhận là người ĐÃ MỜI */
    GROUP_INVITE_ACCEPTED,

    /**
     * Loại CŨ, chỉ còn tồn tại để đọc dữ liệu sinh ra trước khi có bảng phân loại này.
     *
     * <p>Hồi đó frontend tự gọi {@code POST /v1/notifications} và nhét cả bốn thứ — thích bài,
     * bình luận, mời kết bạn, chấp nhận kết bạn — vào cùng một giá trị {@code POST}, lại không kèm
     * id của đối tượng nào. Vì vậy thông báo loại này không bấm được: không có gì để mà mở ra.
     * Không dùng cho bản ghi mới.
     */
    POST
}

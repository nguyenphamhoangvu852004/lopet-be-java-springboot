package com.nguyenvu.lopet.post;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.nguyenvu.lopet.common.exception.BadRequestException;
import com.nguyenvu.lopet.common.exception.ForbiddenException;
import com.nguyenvu.lopet.common.exception.NotFoundException;
import com.nguyenvu.lopet.group.entity.Group;
import com.nguyenvu.lopet.group.entity.GroupType;
import com.nguyenvu.lopet.group.repository.GroupMemberRepository;
import com.nguyenvu.lopet.group.repository.GroupRepository;
import com.nguyenvu.lopet.post.entity.PostScope;

import lombok.RequiredArgsConstructor;

/**
 * Nguồn sự thật cho phía GHI, đối xứng với
 * {@link com.nguyenvu.lopet.post.repository.PostVisibility} của phía ĐỌC.
 *
 * <p>PostVisibility trả lời "ai được xem bài nào" bằng một mệnh đề SQL; lớp này trả lời "ai được
 * tạo/sửa bài ở đâu, với scope nào". Tách riêng vì hai câu hỏi chạy ở hai tầng khác nhau (query vs
 * service) nhưng phải nhất quán với nhau — để rải rác trong controller thì mỗi endpoint mới lại
 * quên một nhánh.
 */
@Component
@RequiredArgsConstructor
public class PostPolicy {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;

    /**
     * Xác định group cho một bài sắp tạo, đồng thời kiểm tra người tạo có quyền đăng vào đó không.
     *
     * <p>Trước bản vá, luồng tạo bài chỉ nạp group rồi gắn vào bài mà KHÔNG hỏi người đăng có phải
     * thành viên hay không, nên bất kỳ tài khoản nào đã đăng nhập cũng đăng được bài vào một group
     * PRIVATE mà mình không thuộc về — và bài đó sau đó hiện ra với toàn bộ thành viên thật của nhóm
     * (nhánh E của visibility).
     *
     * <p>Group không tồn tại → 404 thay vì âm thầm gán {@code group = null}: hành vi cũ biến một bài
     * đáng lẽ thuộc nhóm thành bài cá nhân scope PUBLIC, tức là đẩy nội dung người dùng tưởng đang
     * đăng trong nhóm ra ngoài công khai.
     *
     * <p>Người ngoài đăng vào group PRIVATE → 403 chứ không phải 404: sự tồn tại của group vốn đã
     * công khai qua {@code GET /v1/groups/:id} (route không xác thực), nên che giấu ở đây không giấu
     * được gì mà chỉ làm client khó hiểu lỗi. Khác với bài viết — ở đó 404 là bắt buộc vì id bài là
     * thứ duy nhất cần đoán để dò nội dung riêng tư.
     */
    public Group resolveGroupForPost(Integer groupId, Integer accountId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy nhóm"));

        if (group.getType() == GroupType.PRIVATE
                && groupMemberRepository.findByGroupIdAndAccountId(group.getId(), accountId).isEmpty()) {
            throw new ForbiddenException("Bạn không phải thành viên của nhóm này");
        }
        return group;
    }

    /**
     * Chuẩn hoá scope do client gửi lên và chặn các tổ hợp vô nghĩa.
     *
     * <p>Bài cá nhân: PUBLIC | FRIEND | PRIVATE. Bài nhóm: PUBLIC | PRIVATE. FRIEND bị loại khỏi bài
     * nhóm vì "bạn bè của tác giả" không phải một tập con của nhóm — cho phép nó sẽ tạo ra một chiều
     * hiển thị thứ ba mà không nhánh nào trong visibility diễn tả được, và bài sẽ lặng lẽ biến mất
     * khỏi mọi feed.
     *
     * <p>{@code isGroupPost} PHẢI được suy ra từ group thật của bài (đã nạp từ DB), không bao giờ từ
     * {@code groupId} trong body: bug cũ ở luồng sửa bài chọn danh sách scope hợp lệ theo groupId của
     * request, nên chỉ cần bỏ trống groupId là đặt được scope FRIEND cho một bài đang nằm trong nhóm.
     *
     * <p>Lưu ý về "trần" hiển thị: scope của bài chỉ THU HẸP chứ không nới rộng được phạm vi của
     * nhóm — bài PUBLIC trong nhóm PRIVATE vẫn không lọt ra ngoài vì nhánh B của visibility đòi thêm
     * {@code group.type = PUBLIC}.
     */
    public PostScope parseScope(String rawScope, boolean isGroupPost) {
        List<PostScope> allowed = isGroupPost
                ? List.of(PostScope.PUBLIC, PostScope.PRIVATE)
                : List.of(PostScope.PUBLIC, PostScope.FRIEND, PostScope.PRIVATE);

        return allowed.stream()
                .filter(scope -> scope.name().equals(rawScope))
                .findFirst()
                .orElseThrow(() -> new BadRequestException(
                        "Scope không hợp lệ cho " + (isGroupPost ? "bài nhóm" : "bài cá nhân") + ": "
                                + "chỉ nhận " + allowed.stream().map(Enum::name)
                                        .collect(Collectors.joining(" | "))));
    }
}

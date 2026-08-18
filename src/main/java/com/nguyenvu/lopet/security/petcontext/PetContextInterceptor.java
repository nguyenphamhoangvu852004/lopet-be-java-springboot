package com.nguyenvu.lopet.security.petcontext;

import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import com.nguyenvu.lopet.common.exception.BadRequestException;
import com.nguyenvu.lopet.common.exception.MissingPetHeaderException;
import com.nguyenvu.lopet.common.exception.NoPetOwnedException;
import com.nguyenvu.lopet.common.exception.PetNotOwnedException;
import com.nguyenvu.lopet.pet.repository.PetRepository;
import com.nguyenvu.lopet.security.CurrentUser;
import com.nguyenvu.lopet.security.jwt.UserPrincipal;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Xác định và xác nhận "pet đang thao tác" cho mọi endpoint mang {@link RequirePet}.
 *
 * <p>Chạy SAU {@link com.nguyenvu.lopet.security.AuthInterceptor} (thứ tự đăng ký trong
 * {@code WebConfig}) vì nó cần danh tính đã được xác thực: không có accountId thì không có gì để so
 * quyền sở hữu.
 *
 * <p>Xử lý HAI annotation. {@link RequirePet} cần biết ĐÚNG con pet nào đang hành động, nên bắt
 * buộc có header. {@link RequireAnyPet} chỉ cần "tài khoản đã có pet chưa", nên không đòi header —
 * hai loại endpoint này có hợp đồng API khác nhau và không được gộp làm một.
 *
 * <p><b>Header cũng được xác nhận trên route KHÔNG mang annotation nào</b>, chỉ khác là ở đó nó tuỳ
 * chọn: có thì mở rộng phạm vi thấy được, không có thì người gọi xem ở mức tài khoản. Bỏ qua header
 * ở những route đó — hành vi trước bản vá — làm {@code PetContext.optional()} luôn null trên toàn bộ
 * đường ĐỌC, xem {@link #resolveOptionalPet}.
 *
 * <p>Ba nhánh lỗi, ba mã khác nhau — cố ý không gộp:
 * <ul>
 *   <li>thiếu/sai định dạng header → 400, request dị dạng;</li>
 *   <li>tài khoản chưa có pet nào → 403 kèm hướng dẫn tạo pet, trạng thái người dùng sửa được;</li>
 *   <li>pet không thuộc tài khoản → 403 chung, không tiết lộ pet có tồn tại hay không.</li>
 * </ul>
 *
 * <p>Đây là tầng chặn ở BIÊN. Nó không thay thế guard trong service: một use case gọi thẳng từ nơi
 * khác (job nền, handler socket) không đi qua interceptor nào cả, nên service vẫn phải tự hỏi
 * {@link PetContext}.
 */
@Component
public class PetContextInterceptor implements HandlerInterceptor {

    public static final String HEADER = "X-Pet-Id";

    private final PetOwnerResolver petOwnerResolver;
    private final PetRepository petRepository;

    public PetContextInterceptor(PetOwnerResolver petOwnerResolver, PetRepository petRepository) {
        this.petOwnerResolver = petOwnerResolver;
        this.petRepository = petRepository;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        UserPrincipal caller = CurrentUser.optional();

        if (annotated(handlerMethod, RequirePet.class)) {
            requirePetIdentity(request, caller);
            return true;
        }

        if (annotated(handlerMethod, RequireAnyPet.class)) {
            if (caller == null) {
                // Endpoint có @RequireAnyPet mà thiếu @Auth là lỗi cấu hình, nhưng phản hồi vẫn
                // phải an toàn: không có danh tính thì không xác nhận được sở hữu, nên từ chối.
                throw new PetNotOwnedException();
            }
            if (!petRepository.existsByAccountId(caller.id())) {
                throw new NoPetOwnedException();
            }
        }

        // Mọi route còn lại: header là TUỲ CHỌN nhưng vẫn được xác nhận nếu có.
        resolveOptionalPet(request, caller);
        return true;
    }

    /** Đường đi chặt: {@code @RequirePet} phải biết ĐÚNG con nào, nên thiếu hoặc sai là lỗi. */
    private void requirePetIdentity(HttpServletRequest request, UserPrincipal caller) {
        if (caller == null) {
            throw new PetNotOwnedException();
        }
        Integer petId = parseHeader(request.getHeader(HEADER), caller.id());
        Integer ownerId = petOwnerResolver.ownerAccountIdOf(petId);
        if (ownerId == null || !ownerId.equals(caller.id())) {
            throw new PetNotOwnedException();
        }
        store(petId);
    }

    /**
     * Xác định pet đang thao tác cho các route KHÔNG mang {@code @RequirePet} — gần như toàn bộ
     * đường ĐỌC.
     *
     * <p><b>Vì sao cần.</b> Trước bản vá, interceptor thoát ngay khi route không mang annotation nào,
     * nên {@code PetContext.optional()} LUÔN trả null trên mọi route đọc. Hệ quả không phải rò rỉ mà
     * là hỏng theo chiều đóng, và hỏng im lặng: nhánh "bài trong nhóm mà pet này là thành viên" của
     * {@code PostVisibility} thành code chết, còn {@code GET /v1/groups/{id}} chỉ có thể trả
     * {@code viewerStatus = NONE} — tức là CHÍNH CHỦ NHÓM cũng bị coi là người ngoài và bị hiện nút
     * "gửi yêu cầu tham gia" vào nhóm của mình.
     *
     * <p><b>Vì sao vẫn an toàn.</b> Header chỉ được tin sau khi đã so quyền sở hữu với tài khoản
     * trong token, y như đường đi chặt. Bốn trường hợp dưới đây đều BỎ QUA header thay vì ném lỗi, và
     * bỏ qua nghĩa là người gọi tụt về đúng phạm vi của một người chưa chọn pet — hỏng theo chiều
     * đóng, không mở thêm gì:
     *
     * <ul>
     *   <li>chưa xác thực — không có gì để so, nên một {@code X-Pet-Id} tự khai không bao giờ được
     *       tin. Đây là chỗ chặn việc mạo danh thành viên để đọc nội dung nhóm riêng tư;</li>
     *   <li>header vắng mặt — người gọi chỉ đang xem ở mức tài khoản;</li>
     *   <li>header không phải số — request dị dạng, nhưng một route đọc công khai không nên chết vì
     *       một header vốn là tuỳ chọn;</li>
     *   <li>pet không thuộc người gọi — thường là petId cũ còn sót trong localStorage sau khi đổi
     *       tài khoản. Trả 403 ở đây sẽ làm mọi request đọc gãy cho tới khi client tự dọn, mà việc
     *       cho họ thấy nội dung của một con vật không phải của mình mới là điều sai.</li>
     * </ul>
     */
    private void resolveOptionalPet(HttpServletRequest request, UserPrincipal caller) {
        if (caller == null) {
            return;
        }
        String raw = request.getHeader(HEADER);
        if (raw == null || raw.isBlank()) {
            return;
        }
        Integer petId;
        try {
            petId = Integer.valueOf(raw.trim());
        } catch (NumberFormatException exception) {
            return;
        }
        Integer ownerId = petOwnerResolver.ownerAccountIdOf(petId);
        if (ownerId == null || !ownerId.equals(caller.id())) {
            return;
        }
        store(petId);
    }

    private void store(Integer petId) {
        RequestContextHolder.currentRequestAttributes()
                .setAttribute(PetContext.ATTRIBUTE, petId, RequestAttributes.SCOPE_REQUEST);
    }

    private boolean annotated(HandlerMethod handlerMethod, Class<? extends java.lang.annotation.Annotation> type) {
        return handlerMethod.getMethodAnnotation(type) != null
                || handlerMethod.getBeanType().getAnnotation(type) != null;
    }

    /**
     * Header vắng mặt được phân biệt thành hai lỗi khác nhau tuỳ tài khoản đã có pet hay chưa. Truy
     * vấn {@code existsByAccountId} chỉ chạy ở nhánh LỖI nên nó không nằm trên đường đi thường —
     * request hợp lệ không phải trả giá cho thông điệp lỗi đẹp hơn.
     */
    private Integer parseHeader(String raw, Integer accountId) {
        if (raw == null || raw.isBlank()) {
            if (!petRepository.existsByAccountId(accountId)) {
                throw new NoPetOwnedException();
            }
            throw new MissingPetHeaderException();
        }
        try {
            return Integer.valueOf(raw.trim());
        } catch (NumberFormatException exception) {
            throw new BadRequestException("Header " + HEADER + " phải là một số nguyên");
        }
    }
}

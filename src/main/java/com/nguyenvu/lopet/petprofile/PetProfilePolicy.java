package com.nguyenvu.lopet.petprofile;

import java.util.regex.Pattern;

import com.nguyenvu.lopet.common.exception.BadRequestException;
import com.nguyenvu.lopet.pet.PetPolicy;
import com.nguyenvu.lopet.petprofile.entity.PetVisibility;

/**
 * Chuẩn hoá dữ liệu hồ sơ công khai — đối xứng với {@link PetPolicy} của module thú cưng.
 */
public final class PetProfilePolicy {

    /**
     * Chỉ chữ thường, số và gạch dưới. Không cho dấu chấm và gạch ngang là có chủ đích: handle đi
     * vào URL và vào cú pháp {@code @mention}, mà cả hai nơi đó dấu chấm/gạch ngang đều là ký tự
     * phân tách — {@code @milo.dog} không có cách nào biết được handle kết thúc ở đâu.
     */
    private static final Pattern HANDLE = Pattern.compile("^[a-z0-9_]{3,30}$");

    /**
     * Chuẩn hoá về chữ THƯỜNG trước khi so trùng và trước khi lưu. Nếu không, {@code Milo} và
     * {@code milo} là hai handle khác nhau ở tầng ứng dụng nhưng lại đụng UNIQUE index của MySQL
     * (collation mặc định không phân biệt hoa thường) — lỗi hiện ra thành 500 chứ không phải 409.
     */
    public static String requireHandle(String raw) {
        String normalized = raw == null ? null : raw.trim().toLowerCase();
        if (normalized == null || normalized.isEmpty()) {
            throw new BadRequestException("\"handle\" is required");
        }
        if (!HANDLE.matcher(normalized).matches()) {
            throw new BadRequestException(
                    "\"handle\" chỉ được chứa chữ thường, số và dấu gạch dưới, dài 3-30 ký tự");
        }
        return normalized;
    }

    public static String requireDisplayName(String raw) {
        String trimmed = raw == null ? null : raw.trim();
        if (trimmed == null || trimmed.isEmpty()) {
            throw new BadRequestException("\"displayName\" is not allowed to be empty");
        }
        if (trimmed.length() > 50) {
            throw new BadRequestException(
                    "\"displayName\" length must be less than or equal to 50 characters long");
        }
        return trimmed;
    }

    /** Bỏ trống thì mặc định PUBLIC, đúng như {@code postScope} mặc định của bài viết */
    public static PetVisibility parseVisibility(String raw) {
        return raw == null || raw.isEmpty()
                ? PetVisibility.PUBLIC
                : PetPolicy.parseEnum(PetVisibility.class, raw, "visibility");
    }

    /**
     * Handle sinh tự động cho hồ sơ được tạo kèm thú cưng. Dựa trên id nên chắc chắn không trùng và
     * không phải thử lại vòng lặp; người dùng đổi lại sau bằng {@code PUT /v1/pet-profiles/{petId}}.
     *
     * <p>Không lấy theo {@code pet.name}: tên thú cưng cho phép dấu tiếng Việt, khoảng trắng và
     * trùng lặp thoải mái — ba thứ mà handle đều cấm.
     */
    public static String seedHandle(Integer petId) {
        return "pet_" + petId;
    }

    private PetProfilePolicy() {
    }
}

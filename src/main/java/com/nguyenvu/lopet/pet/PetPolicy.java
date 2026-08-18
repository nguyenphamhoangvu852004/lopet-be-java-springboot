package com.nguyenvu.lopet.pet;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.stream.Collectors;

import com.nguyenvu.lopet.common.exception.BadRequestException;
import com.nguyenvu.lopet.pet.entity.PetGender;
import com.nguyenvu.lopet.pet.entity.PetSpecies;

/**
 * Chuẩn hoá dữ liệu người dùng gửi lên trước khi nó chạm tới entity — đối xứng với
 * {@link com.nguyenvu.lopet.post.PostPolicy} của module bài viết.
 *
 * <p>Là lớp tĩnh chứ không phải {@code @Component} vì không phụ thuộc repository nào, giống
 * {@link com.nguyenvu.lopet.security.authz.PermissionCatalog}.
 */
public final class PetPolicy {

    public static PetSpecies parseSpecies(String raw) {
        return parseEnum(PetSpecies.class, raw, "species");
    }

    public static PetGender parseGender(String raw) {
        return parseEnum(PetGender.class, raw, "gender");
    }

    /**
     * Cắt khoảng trắng thừa. Bean Validation đã chặn chuỗi rỗng, nhưng {@code @NotBlank} coi
     * {@code "   Milo  "} là hợp lệ và lưu nguyên như vậy thì tên hiển thị sẽ lệch.
     */
    public static String requireName(String raw) {
        String trimmed = raw == null ? null : raw.trim();
        if (blank(trimmed)) {
            throw new BadRequestException("\"name\" is not allowed to be empty");
        }
        if (trimmed.length() > 50) {
            throw new BadRequestException("\"name\" length must be less than or equal to 50 characters long");
        }
        return trimmed;
    }

    /** Trường tuỳ chọn: chuỗi toàn khoảng trắng lưu thành NULL chứ không phải chuỗi rỗng */
    public static String trimToNull(String raw) {
        String trimmed = raw == null ? null : raw.trim();
        return blank(trimmed) ? null : trimmed;
    }

    /**
     * Ngày sinh là BẮT BUỘC vì cột {@code pets.date_of_birth} khai NOT NULL.
     *
     * <p>Yêu cầu nghiệp vụ muốn cho phép "không rõ ngày sinh" — đó là một thay đổi SCHEMA
     * (drop NOT NULL), cố ý không tự làm ở đây. Xem mục "Lệch thiết kế" trong
     * {@code docs/PET_MANAGEMENT.md}.
     */
    public static LocalDate requireDateOfBirth(LocalDate value) {
        if (value == null) {
            throw new BadRequestException("\"dateOfBirth\" is required");
        }
        if (value.isAfter(LocalDate.now())) {
            throw new BadRequestException("\"dateOfBirth\" must be less than or equal to today");
        }
        return value;
    }

    /**
     * Ném 400 kèm danh sách giá trị hợp lệ thay vì để Jackson ném ra 500. Chuẩn hoá về chữ hoa để
     * {@code "dog"} và {@code "DOG"} là một — client cũ gửi chữ thường không phải là lỗi nghiệp vụ.
     *
     * <p>{@code public} vì {@code PetProfilePolicy} dùng lại cho {@code visibility}. Cả hai lớp
     * policy phải cho ra CÙNG một thông điệp lỗi khi client gửi enum sai, nếu không hai module cùng
     * nói về một con vật lại trả về hai định dạng lỗi khác nhau.
     */
    public static <E extends Enum<E>> E parseEnum(Class<E> type, String raw, String field) {
        String normalized = raw == null ? null : raw.trim().toUpperCase();
        return Arrays.stream(type.getEnumConstants())
                .filter(value -> value.name().equals(normalized))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("\"" + field + "\" must be one of ["
                        + Arrays.stream(type.getEnumConstants()).map(Enum::name)
                                .collect(Collectors.joining(", ")) + "]"));
    }

    private static boolean blank(String value) {
        return value == null || value.isEmpty();
    }

    private PetPolicy() {
    }
}

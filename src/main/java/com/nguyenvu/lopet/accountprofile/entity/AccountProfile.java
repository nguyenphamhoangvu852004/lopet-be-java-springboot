package com.nguyenvu.lopet.accountprofile.entity;

import java.time.LocalDate;

import org.hibernate.annotations.SQLRestriction;

import com.nguyenvu.lopet.account.entity.Account;
import com.nguyenvu.lopet.common.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "account_profiles")
@SQLRestriction("deletedAt is null")
public class AccountProfile extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "fullName")
    private String fullName;

    @Column(name = "phoneNumber")
    private String phoneNumber;

    @Column(name = "bio", columnDefinition = "text")
    private String bio;

    /** tinyint nullable — giữ Integer để không mất được giá trị NULL */
    @Column(name = "sex", columnDefinition = "tinyint")
    private Integer sex;

    @Column(name = "dateOfBirth", columnDefinition = "date")
    private LocalDate dateOfBirth;

    @Column(name = "hometown")
    private String hometown;

    @Column(name = "avatarUrl", columnDefinition = "text")
    private String avatarUrl;

    @Column(name = "coverUrl", columnDefinition = "text")
    private String coverUrl;

    /**
     * Mặc định PUBLIC để hàng cũ và hàng do {@code AccountProfileFactory} tạo lúc đăng ký không phải
     * khai lại — đồng thời khớp {@code default 'PUBLIC'} của cột, nên dữ liệu đã có sẵn không cần
     * backfill.
     *
     * <p>Cột này KHÔNG tự bảo vệ được gì: nó chỉ là dữ liệu. Việc thực thi nằm ở
     * {@link com.nguyenvu.lopet.accountprofile.repository.AccountProfileVisibilityFilter}, và đó là
     * chỗ DUY NHẤT được phép trả lời "người này có xem được hồ sơ kia không".
     */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false,
            columnDefinition = "enum('PUBLIC','FRIEND','PRIVATE') not null default 'PUBLIC'")
    private ProfileVisibility visibility = ProfileVisibility.PUBLIC;

    /**
     * Phía nghịch của quan hệ 1-1: khoá ngoại nằm ở {@code accounts.profileId}. Tầng ownership cần
     * biết hồ sơ này thuộc tài khoản nào, nên quan hệ phải tồn tại dù không sinh thêm cột nào.
     */
    @OneToOne(mappedBy = "accountProfile", fetch = FetchType.LAZY)
    private Account account;
}

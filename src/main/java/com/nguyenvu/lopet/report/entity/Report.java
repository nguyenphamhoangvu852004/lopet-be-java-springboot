package com.nguyenvu.lopet.report.entity;

import com.nguyenvu.lopet.account.entity.Account;
import com.nguyenvu.lopet.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@Data
@SuperBuilder
@NoArgsConstructor()
@AllArgsConstructor()
@Entity
@Table(name = "reports")
public class Report extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @JoinColumn(nullable = false)
    private String targetId;

    @JoinColumn(nullable = false)
    private String targetType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private Account reporter;

    @ManyToOne(fetch = FetchType.LAZY)
    private Account resolver;

    @JoinColumn(nullable = false)
    private String reason;


    @JoinColumn(nullable = false)
    private String status;

    public static Report create(Account reporter, String reason) {
        return Report.builder()
                .reporter(reporter)
                .reason(reason)
                .resolver(null)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    public Report update(Report newReport) {
        this.id = newReport.getId();
        this.targetId = newReport.getTargetId();
        this.targetType = newReport.getTargetType();
        this.reporter = newReport.getReporter();
        this.resolver = newReport.getResolver();
        this.reason = newReport.getReason();
        return this;
    }

}

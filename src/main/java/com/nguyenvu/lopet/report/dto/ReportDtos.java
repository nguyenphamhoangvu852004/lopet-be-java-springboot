package com.nguyenvu.lopet.report.dto;

import java.time.LocalDateTime;

import com.nguyenvu.lopet.account.dto.AccountViews.AccountBrief;
import com.nguyenvu.lopet.report.entity.ReportAction;
import com.nguyenvu.lopet.report.entity.ReportType;

public final class ReportDtos {

    public record CreateReportRequest(Integer targetId, String type, String reason) {
    }

    /** {@code message} nằm TRONG data và đồng thời được dùng làm message của envelope */
    public record CreateReportResponse(
            Integer accountId,
            Integer targetId,
            ReportType type,
            String reason,
            String message) {
    }

    public record UpdateReportRequest(String type, String action) {
    }

    /** {@code id} ở đây là targetId chứ không phải id của bản ghi report */
    public record UpdateReportResponse(Integer id) {
    }

    /** GET /v1/reports trả thẳng entity kèm reporter và resolvedBy */
    public record ReportEntity(
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            LocalDateTime deletedAt,
            Integer id,
            AccountBrief reporter,
            String reason,
            ReportType targetType,
            Integer targetId,
            ReportAction action,
            AccountBrief resolvedBy,
            LocalDateTime resolvedAt) {
    }

    private ReportDtos() {
    }
}

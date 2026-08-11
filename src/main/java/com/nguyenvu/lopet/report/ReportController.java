package com.nguyenvu.lopet.report;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.nguyenvu.lopet.common.response.ApiResponse;
import com.nguyenvu.lopet.report.dto.ReportDtos;
import com.nguyenvu.lopet.security.Auth;
import com.nguyenvu.lopet.security.CurrentUser;
import com.nguyenvu.lopet.security.RequirePermission;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    /** Xem danh sách báo cáo là việc của staff — trước bản vá chỉ cần đăng nhập là xem được */
    @GetMapping
    @Auth
    @RequirePermission("report:read")
    public ApiResponse<List<ReportDtos.ReportEntity>> getList(
            @RequestParam(required = false) Integer accountId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Integer targetId) {
        return ApiResponse.ok("Get list report successfully", reportService.getList(accountId, type, targetId));
    }

    /** Gửi báo cáo thuộc baseline: mọi tài khoản đã đăng nhập đều được */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Auth
    @RequirePermission("report:create")
    public ApiResponse<ReportDtos.CreateReportResponse> create(
            @RequestBody ReportDtos.CreateReportRequest request) {
        ReportDtos.CreateReportResponse response = reportService.create(CurrentUser.require().id(),
                request.targetId(), request.type(), request.reason());
        // message của envelope lấy từ chính data.message — bản TS dùng `response?.message ?? 'CREATED'`
        return ApiResponse.created(response.message(), response);
    }

    @PutMapping("/{targetId}")
    @Auth
    @RequirePermission("report:resolve")
    public ApiResponse<ReportDtos.UpdateReportResponse> update(
            @PathVariable Integer targetId, @RequestBody ReportDtos.UpdateReportRequest request) {
        return ApiResponse.ok("Update report successfully",
                reportService.update(targetId, request.type(), request.action(),
                        CurrentUser.require().id()));
    }
}

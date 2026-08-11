package com.nguyenvu.lopet.report;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nguyenvu.lopet.account.AccountMapper;
import com.nguyenvu.lopet.account.entity.Account;
import com.nguyenvu.lopet.account.repository.AccountRepository;
import com.nguyenvu.lopet.common.exception.BadRequestException;
import com.nguyenvu.lopet.report.dto.ReportDtos;
import com.nguyenvu.lopet.report.entity.Report;
import com.nguyenvu.lopet.report.entity.ReportAction;
import com.nguyenvu.lopet.report.entity.ReportType;
import com.nguyenvu.lopet.report.repository.ReportRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;
    private final AccountRepository accountRepository;

    /**
     * Cố ý KHÔNG kiểm tra target (bài/nhóm/tài khoản bị báo cáo) có tồn tại hay không — bản TS cũng
     * không, dù service của nó có sẵn cả postRepo lẫn groupRepo. Thêm kiểm tra ở đây sẽ biến những
     * báo cáo đang gửi được thành lỗi 400.
     */
    @Transactional
    public ReportDtos.CreateReportResponse create(Integer reporterId, Integer targetId, String type,
                                                   String reason) {
        Account reporter = accountRepository.findById(reporterId).orElseThrow(BadRequestException::new);

        Report saved = reportRepository.save(Report.builder()
                .reporter(reporter)
                .reason(reason)
                .targetType(parseType(type))
                .targetId(targetId)
                .action(ReportAction.PENDING)
                .build());

        return new ReportDtos.CreateReportResponse(saved.getReporter().getId(), saved.getTargetId(),
                saved.getTargetType(), saved.getReason(), "Created Success");
    }

    @Transactional(readOnly = true)
    public List<ReportDtos.ReportEntity> getList(Integer accountId, String type, Integer targetId) {
        return reportRepository.search(accountId, type == null ? null : parseType(type), targetId).stream()
                .map(this::toEntityView)
                .toList();
    }

    /**
     * Cập nhật TẤT CẢ báo cáo khớp cặp (targetId, targetType) chứ không phải một bản ghi: nhiều
     * người có thể cùng báo cáo một nội dung, và một quyết định kiểm duyệt đóng lại toàn bộ số đó.
     */
    @Transactional
    public ReportDtos.UpdateReportResponse update(Integer targetId, String type, String action,
                                                   Integer resolvedBy) {
        List<Report> reports = reportRepository.search(null, parseType(type), targetId);

        Account resolver = resolvedBy == null ? null : accountRepository.getReferenceById(resolvedBy);
        for (Report report : reports) {
            report.setAction(parseAction(action));
            if (resolver != null) {
                // Ghi lại staff nào đã xử lý — bắt buộc với hành động kiểm duyệt
                report.setResolvedBy(resolver);
                report.setResolvedAt(LocalDateTime.now());
            }
            reportRepository.save(report);
        }

        return new ReportDtos.UpdateReportResponse(targetId);
    }

    private ReportType parseType(String value) {
        try {
            return ReportType.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BadRequestException("Loại báo cáo không hợp lệ: " + value);
        }
    }

    private ReportAction parseAction(String value) {
        try {
            return ReportAction.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BadRequestException("Hành động không hợp lệ: " + value);
        }
    }

    private ReportDtos.ReportEntity toEntityView(Report report) {
        return new ReportDtos.ReportEntity(report.getCreatedAt(), report.getUpdatedAt(),
                report.getDeletedAt(), report.getId(), AccountMapper.toBrief(report.getReporter()),
                report.getReason(), report.getTargetType(), report.getTargetId(), report.getAction(),
                AccountMapper.toBrief(report.getResolvedBy()), report.getResolvedAt());
    }
}

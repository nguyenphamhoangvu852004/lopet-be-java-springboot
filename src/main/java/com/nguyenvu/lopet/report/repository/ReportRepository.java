package com.nguyenvu.lopet.report.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nguyenvu.lopet.report.entity.Report;
import com.nguyenvu.lopet.report.entity.ReportType;

public interface ReportRepository extends JpaRepository<Report, Integer> {

    /** Ba bộ lọc đều tuỳ chọn, đúng như {@code whereClause} dựng động bên TS */
    @Query("""
            select r from Report r
            left join fetch r.reporter
            left join fetch r.resolvedBy
            where (:accountId is null or r.reporter.id = :accountId)
              and (:targetType is null or r.targetType = :targetType)
              and (:targetId is null or r.targetId = :targetId)
            """)
    List<Report> search(@Param("accountId") Integer accountId,
                        @Param("targetType") ReportType targetType,
                        @Param("targetId") Integer targetId);
}

package com.nguyenvu.lopet.llms;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.nguyenvu.lopet.statistic.StatisticService;
import com.nguyenvu.lopet.statistic.dto.StatisticDtos;

@Component
public class StatisticTool {

    private static final int DEFAULT_DAYS = 7;
    private static final int MAX_DAYS = 365;

    private final StatisticService service;

    public StatisticTool(StatisticService service) {
        this.service = service;
    }

    @Tool(description = "System-wide account statistics: total, active, banned"
            + " and deleted counts. Use it when the account breakdown matters, not just the total.")
    public StatisticDtos.AccountBreakdown getAccountBreakdown() {
        return service.getAccountBreakdown();
    }

    @Tool(description = "System-wide engagement statistics: total likes and total comments.")
    public StatisticDtos.EngagementSummary getEngagementSummary() {
        return service.getEngagementSummary();
    }


    @Tool(description = "Count new accounts, new posts and new comments over the last N days."
            + " Use it when recent growth rate matters.")
    public StatisticDtos.RecentActivity getRecentActivity(
            @ToolParam(description = "Number of days to look back from today, defaults to 7 and caps at 365",
                    required = false) Integer days) {
        return service.getRecentActivity(normalizeDays(days));
    }

    private int normalizeDays(Integer days) {
        if (days == null || days < 1) {
            return DEFAULT_DAYS;
        }
        return Math.min(days, MAX_DAYS);
    }
}

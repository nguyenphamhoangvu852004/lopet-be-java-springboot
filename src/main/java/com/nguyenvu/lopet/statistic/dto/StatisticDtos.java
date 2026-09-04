package com.nguyenvu.lopet.statistic.dto;

public final class StatisticDtos {

    public record AccountBreakdown(long total, long active, long banned, long deleted) {
    }

    public record EngagementSummary(long likes, long comments) {
    }

    public record RecentActivity(int days, long newAccounts, long newPosts, long newComments) {
    }

    private StatisticDtos() {
    }
}

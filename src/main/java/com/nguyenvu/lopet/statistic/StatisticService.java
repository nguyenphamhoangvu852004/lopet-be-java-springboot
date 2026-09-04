package com.nguyenvu.lopet.statistic;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nguyenvu.lopet.account.repository.AccountRepository;
import com.nguyenvu.lopet.comment.repository.CommentRepository;
import com.nguyenvu.lopet.post.repository.PostLikeRepository;
import com.nguyenvu.lopet.post.repository.PostRepository;
import com.nguyenvu.lopet.statistic.dto.StatisticDtos;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class StatisticService {

    private static final int ACTIVE = 0;

    private final AccountRepository accountRepository;
    private final PostRepository postRepository;
    private final PostLikeRepository postLikeRepository;
    private final CommentRepository commentRepository;

    @Transactional(readOnly = true)
    public StatisticDtos.AccountBreakdown getAccountBreakdown() {
        long alive = accountRepository.countByDeletedAtIsNull();
        long active = accountRepository.countByDeletedAtIsNullAndIsBanned(ACTIVE);
        return new StatisticDtos.AccountBreakdown(
                accountRepository.count(),
                active,
                alive - active,
                accountRepository.countByDeletedAtIsNotNull());
    }

    @Transactional(readOnly = true)
    public StatisticDtos.EngagementSummary getEngagementSummary() {
        return new StatisticDtos.EngagementSummary(
                postLikeRepository.count(),
                commentRepository.countByDeletedAtIsNull());
    }

    @Transactional(readOnly = true)
    public StatisticDtos.RecentActivity getRecentActivity(int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        return new StatisticDtos.RecentActivity(
                days,
                accountRepository.countByCreatedAtGreaterThanEqual(since),
                postRepository.countByCreatedAtGreaterThanEqual(since),
                commentRepository.countByCreatedAtGreaterThanEqual(since));
    }
}

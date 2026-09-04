package com.nguyenvu.lopet.accountprofile;

import java.time.Duration;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.nguyenvu.lopet.accountprofile.dto.AccountProfileDtos;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class AccountProfileCache {

    private static final String SUMMARY_KEY_PREFIX = "account_profile:summary:";
    private static final String PUBLIC_KEY_PREFIX = "account_profile:public:";

    private static final Duration SUMMARY_TTL = Duration.ofMinutes(10);
    private static final Duration PUBLIC_TTL = Duration.ofMinutes(1);

    private final StringRedisTemplate redis;
    private final JsonMapper jsonMapper;

    public String summaryKey(Integer accountId) {
        return SUMMARY_KEY_PREFIX + accountId;
    }

    public String publicKey(Integer accountId) {
        return PUBLIC_KEY_PREFIX + accountId;
    }

    public AccountProfileDtos.ProfileSummary findSummary(Integer accountId) {
        String cached = redis.opsForValue().get(summaryKey(accountId));
        return cached == null ? null : jsonMapper.readValue(cached, AccountProfileDtos.ProfileSummary.class);
    }

    public void saveSummary(Integer accountId, AccountProfileDtos.ProfileSummary summary) {
        redis.opsForValue().set(summaryKey(accountId), jsonMapper.writeValueAsString(summary), SUMMARY_TTL);
    }

    /**
     * Một khoá phẳng cho mỗi tài khoản.
     *
     * Bản trước là hash chia theo người xem, vì cùng một hồ sơ hiện ra khác nhau
     * tuỳ quan hệ. Không còn lọc theo người xem thì mọi người thấy đúng một bản,
     * và giữ chiều `viewerId` chỉ nhân bản cùng một giá trị lên n lần trong Redis.
     */
    public AccountProfileDtos.PublicProfile findPublic(Integer accountId) {
        String cached = redis.opsForValue().get(publicKey(accountId));
        return cached == null ? null : jsonMapper.readValue(cached, AccountProfileDtos.PublicProfile.class);
    }

    public void savePublic(Integer accountId, AccountProfileDtos.PublicProfile profile) {
        redis.opsForValue().set(publicKey(accountId), jsonMapper.writeValueAsString(profile), PUBLIC_TTL);
    }

    public void evict(Integer accountId) {
        redis.delete(List.of(summaryKey(accountId), publicKey(accountId)));
    }
}

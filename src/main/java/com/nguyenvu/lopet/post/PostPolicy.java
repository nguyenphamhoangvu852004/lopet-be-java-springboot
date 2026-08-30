package com.nguyenvu.lopet.post;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.nguyenvu.lopet.common.exception.BadRequestException;
import com.nguyenvu.lopet.post.entity.PostScope;

@Component
public class PostPolicy {

    public PostScope parseScope(String rawScope) {
        List<PostScope> allowed = List.of(PostScope.PUBLIC, PostScope.FRIEND, PostScope.PRIVATE);

        return allowed.stream()
                .filter(scope -> scope.name().equals(rawScope))
                .findFirst()
                .orElseThrow(() -> new BadRequestException(
                        "Scope không hợp lệ: chỉ nhận "
                                + allowed.stream().map(Enum::name).collect(Collectors.joining(" | "))));
    }
}

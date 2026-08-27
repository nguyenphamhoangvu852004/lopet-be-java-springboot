package com.nguyenvu.lopet.security.authz;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.nguyenvu.lopet.common.exception.ForbiddenException;
import com.nguyenvu.lopet.common.exception.NotFoundException;
import com.nguyenvu.lopet.role.entity.RoleName;
import com.nguyenvu.lopet.security.CurrentUser;
import com.nguyenvu.lopet.security.jwt.UserPrincipal;

public final class OwnershipGuard {

    public static final Set<RoleName> DEFAULT_BYPASS = Set.of(RoleName.ADMIN);
    public static final Set<RoleName> NO_BYPASS = Set.of();

    public static <T> T check(Supplier<T> loader, Function<T, Collection<Integer>> ownersOf, Set<RoleName> bypassRoles) {
        UserPrincipal caller = CurrentUser.optional();
        if (caller == null) {
            throw new ForbiddenException("Chưa xác thực");
        }

        boolean bypassed = caller.roles().stream()
                .anyMatch(role -> bypassRoles.stream().anyMatch(bypass -> bypass.name().equals(role)));
        if (bypassed) {
            return null;
        }

        T resource = loader.get();
        if (resource == null) {
            throw new NotFoundException("Không tìm thấy tài nguyên");
        }

        Set<Integer> allowed = ownersOf.apply(resource).stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (!allowed.contains(caller.id())) {
            throw new ForbiddenException("Bạn không sở hữu tài nguyên này");
        }
        return resource;
    }

    public static <T> T checkSingleOwner(Supplier<T> loader, Function<T, Integer> ownerOf, Set<RoleName> bypassRoles) {
        return check(loader, resource -> List.of(ownerOf.apply(resource)).stream().filter(Objects::nonNull).toList(),
                bypassRoles);
    }

    public static Collection<Integer> owners(Integer... ids) {
        return Arrays.stream(ids).filter(Objects::nonNull).toList();
    }

    private OwnershipGuard() {
    }
}

package com.nguyenvu.lopet.security.rebac;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.nguyenvu.lopet.common.exception.ForbiddenException;
import com.nguyenvu.lopet.common.exception.NotFoundException;
import com.nguyenvu.lopet.security.CurrentUser;
import com.nguyenvu.lopet.security.jwt.UserPrincipal;

@Component
public class RebacEngine {

    private final Map<String, RelationResolver> resolversByType;

    public RebacEngine(List<RelationResolver> resolvers) {
        Map<String, RelationResolver> byType = new HashMap<>();
        for (RelationResolver resolver : resolvers) {
            RelationResolver previous = byType.put(resolver.type(), resolver);
            if (previous != null) {
                throw new IllegalStateException("Hai resolver cùng khai type '" + resolver.type() + "': "
                        + previous.getClass().getName() + " và " + resolver.getClass().getName());
            }
        }
        this.resolversByType = Map.copyOf(byType);
    }

    public boolean check(String action, ObjectRef object) {
        try {
            require(action, object);
            return true;
        } catch (ForbiddenException | NotFoundException denied) {
            return false;
        }
    }

    public void require(String action, ObjectRef object) {
        RebacModel.Rule rule = RebacModel.rule(action);
        UserPrincipal caller = CurrentUser.optional();

        if (rule.requiresAuth() && caller == null) {
            throw new ForbiddenException("Chưa xác thực");
        }

        Set<Relation> platformRelations = relationsOf(ObjectRef.platform(), caller).orElse(Set.of());
        if (grants(platformRelations, rule.platformRelations())) {
            return;
        }

        if (object.isPlatform()) {
            throw new ForbiddenException("Thiếu quyền: " + action);
        }

        Set<Relation> objectRelations = relationsOf(object, caller)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy tài nguyên"));

        if (!grants(objectRelations, rule.objectRelations())) {
            throw new ForbiddenException("Bạn không sở hữu tài nguyên này");
        }
    }

    public Optional<Set<Relation>> relationsOf(ObjectRef object, UserPrincipal caller) {
        RelationResolver resolver = resolversByType.get(object.type());
        if (resolver == null) {
            throw new IllegalStateException("Chưa có RelationResolver cho loại đối tượng: " + object.type());
        }
        return resolver.relationsOf(object.id(), caller);
    }

    private boolean grants(Set<Relation> held, Set<Relation> required) {
        return !required.isEmpty() && !Collections.disjoint(held, required);
    }
}

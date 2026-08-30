package com.nguyenvu.lopet.security.rebac.resolver;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.nguyenvu.lopet.role.entity.RoleName;
import com.nguyenvu.lopet.security.jwt.UserPrincipal;
import com.nguyenvu.lopet.security.rebac.ObjectRef;
import com.nguyenvu.lopet.security.rebac.Relation;
import com.nguyenvu.lopet.security.rebac.RelationResolver;

@Component
public class PlatformRelationResolver implements RelationResolver {

    @Override
    public String type() {
        return ObjectRef.PLATFORM;
    }

    @Override
    public Optional<Set<Relation>> relationsOf(Integer objectId, UserPrincipal caller) {
        if (caller == null) {
            return Optional.of(Set.of());
        }

        EnumSet<Relation> relations = EnumSet.of(Relation.AUTHENTICATED);
        for (String role : caller.roles()) {
            if (RoleName.ADMIN.name().equals(role)) {
                relations.add(Relation.ADMIN);
            } else if (RoleName.MODERATOR.name().equals(role)) {
                relations.add(Relation.MODERATOR);
            } else if (RoleName.SUPPORT.name().equals(role)) {
                relations.add(Relation.SUPPORT);
            }
        }
        return Optional.of(relations);
    }
}

package com.nguyenvu.lopet.security.rebac;

import java.util.Map;
import java.util.Set;

public final class RebacModel {

    public record Rule(Set<Relation> platformRelations, Set<Relation> objectRelations, boolean requiresAuth) {
    }

    public static final String POST_CREATE = "post:create";
    public static final String POST_UPDATE = "post:update";
    public static final String POST_DELETE = "post:delete";
    public static final String COMMENT_CREATE = "comment:create";
    public static final String COMMENT_DELETE = "comment:delete";
    public static final String MESSAGE_READ = "message:read";

    private static final Map<String, Rule> RULES = Map.of(
            POST_CREATE, new Rule(Set.of(Relation.AUTHENTICATED), Set.of(), true),

            POST_UPDATE, new Rule(Set.of(), Set.of(Relation.OWNER), true),

            POST_DELETE, new Rule(Set.of(Relation.ADMIN, Relation.MODERATOR), Set.of(Relation.OWNER), true),

            COMMENT_CREATE, new Rule(Set.of(Relation.AUTHENTICATED), Set.of(), true),

            COMMENT_DELETE, new Rule(Set.of(Relation.ADMIN, Relation.MODERATOR), Set.of(Relation.OWNER), true),

            MESSAGE_READ, new Rule(Set.of(), Set.of(Relation.OWNER), true));

    public static Rule rule(String action) {
        Rule rule = RULES.get(action);
        if (rule == null) {
            throw new IllegalStateException("Chưa khai báo luật ReBAC cho action: " + action);
        }
        return rule;
    }

    public static Set<String> actions() {
        return RULES.keySet();
    }

    private RebacModel() {
    }
}

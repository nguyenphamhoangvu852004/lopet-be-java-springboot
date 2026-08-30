package com.nguyenvu.lopet.security.rebac.resolver;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.nguyenvu.lopet.message.entity.Message;
import com.nguyenvu.lopet.message.repository.MessageRepository;
import com.nguyenvu.lopet.security.jwt.UserPrincipal;
import com.nguyenvu.lopet.security.rebac.ObjectRef;
import com.nguyenvu.lopet.security.rebac.Relation;
import com.nguyenvu.lopet.security.rebac.RelationResolver;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class MessageRelationResolver implements RelationResolver {

    private final MessageRepository messageRepository;

    @Override
    public String type() {
        return ObjectRef.MESSAGE;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Set<Relation>> relationsOf(Integer objectId, UserPrincipal caller) {
        return messageRepository.findDetailById(objectId)
                .map(message -> relations(message, viewerIdOf(caller)));
    }

    private Set<Relation> relations(Message message, Integer viewerId) {
        EnumSet<Relation> relations = EnumSet.noneOf(Relation.class);
        if (viewerId == null) {
            return relations;
        }

        Integer senderId = message.getSender() == null ? null : message.getSender().getId();
        Integer receiverId = message.getReceiver() == null ? null : message.getReceiver().getId();
        if (viewerId.equals(senderId) || viewerId.equals(receiverId)) {
            relations.add(Relation.OWNER);
        }
        return relations;
    }
}

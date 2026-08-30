package com.nguyenvu.lopet.account;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nguyenvu.lopet.account.dto.AccountViews;
import com.nguyenvu.lopet.account.dto.GetAccountResponse;
import com.nguyenvu.lopet.account.dto.IdResponse;
import com.nguyenvu.lopet.account.entity.Account;
import com.nguyenvu.lopet.account.entity.AccountRole;
import com.nguyenvu.lopet.account.repository.AccountRepository;
import com.nguyenvu.lopet.account.repository.AccountRoleRepository;
import com.nguyenvu.lopet.common.exception.BadRequestException;
import com.nguyenvu.lopet.common.exception.NotFoundException;
import com.nguyenvu.lopet.friendship.entity.Friendship;
import com.nguyenvu.lopet.friendship.repository.FriendshipRepository;
import com.nguyenvu.lopet.role.entity.Role;
import com.nguyenvu.lopet.role.entity.RoleName;
import com.nguyenvu.lopet.role.repository.RoleRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final AccountRoleRepository accountRoleRepository;
    private final RoleRepository roleRepository;
    private final FriendshipRepository friendshipRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(readOnly = true)
    public GetAccountResponse getById(Integer id) {
        Account account = accountRepository.findDetailById(id).orElseThrow(NotFoundException::new);
        return AccountMapper.toGetAccountResponse(account);
    }

    @Transactional(readOnly = true)
    public List<AccountViews.AccountListItem> getList() {
        List<Account> accounts = accountRepository.findAllDetail().stream()
                .filter(account -> account.getAccountRoles().stream()
                        .noneMatch(accountRole -> accountRole.getRole().getName() == RoleName.ADMIN))
                .toList();

        if (accounts.isEmpty()) {
            return List.of();
        }

        List<Integer> ids = accounts.stream().map(Account::getId).toList();
        List<Friendship> friendships = friendshipRepository.findAllInvolving(ids);

        Map<Integer, List<Friendship>> sent = friendships.stream()
                .filter(friendship -> friendship.getSender() != null)
                .collect(Collectors.groupingBy(friendship -> friendship.getSender().getId()));
        Map<Integer, List<Friendship>> received = friendships.stream()
                .filter(friendship -> friendship.getReceiver() != null)
                .collect(Collectors.groupingBy(friendship -> friendship.getReceiver().getId()));

        return accounts.stream()
                .map(account -> AccountMapper.toListItem(account, sent, received))
                .toList();
    }

    @Transactional
    public IdResponse ban(Integer id) {
        return setBanned(id, 1);
    }

    @Transactional
    public IdResponse unban(Integer id) {
        return setBanned(id, 0);
    }

    private IdResponse setBanned(Integer id, int value) {
        Account account = accountRepository.findDetailById(id).orElseThrow(BadRequestException::new);
        account.setIsBanned(value);
        return new IdResponse(accountRepository.save(account).getId());
    }

    @Transactional
    public IdResponse delete(Integer id) {
        Account account = accountRepository.findDetailById(id).orElseThrow(BadRequestException::new);
        accountRepository.delete(account);
        return new IdResponse(id);
    }

    @Transactional(readOnly = true)
    public List<AccountViews.AccountBrief> getSuggest(Integer callerId, Integer limit) {
        if (accountRepository.findDetailById(callerId).isEmpty()) {
            return List.of();
        }
        List<Account> accounts = (limit == null || limit <= 0)
                ? accountRepository.findSuggestions(callerId)
                : accountRepository.findSuggestions(callerId, limit);

        return accounts.stream().map(AccountMapper::toBrief).toList();
    }

    @Transactional
    public AccountViews.AccountDetail setRoles(Integer userId, List<String> roleNames, Integer grantedBy) {
        Account account = accountRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Account not found"));

        List<Role> roles = new ArrayList<>();
        for (String name : roleNames == null ? List.<String>of() : roleNames) {
            RoleName roleName;
            try {
                roleName = RoleName.valueOf(name);
            } catch (IllegalArgumentException exception) {
                throw new NotFoundException("Role " + name + " not found");
            }
            roles.add(roleRepository.findByName(roleName)
                    .orElseThrow(() -> new NotFoundException("Role " + name + " not found")));
        }

        accountRoleRepository.findByAccountId(userId).forEach(entityManager::detach);
        accountRoleRepository.deleteByAccountId(userId);
        accountRoleRepository.flush();

        Account granter = grantedBy == null ? null : accountRepository.getReferenceById(grantedBy);
        for (Role role : roles) {
            entityManager.persist(AccountRole.builder()
                    .accountId(userId)
                    .roleId(role.getId())
                    .account(account)
                    .role(role)
                    .grantedBy(granter)
                    .grantedAt(LocalDateTime.now())
                    .build());
        }
        entityManager.flush();
        entityManager.clear();

        Account reloaded = accountRepository.findDetailById(userId).orElse(account);
        return AccountMapper.toDetail(reloaded);
    }
}

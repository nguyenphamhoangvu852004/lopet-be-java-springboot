package com.nguyenvu.lopet.account;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nguyenvu.lopet.account.dto.AccountViews;
import com.nguyenvu.lopet.account.dto.GetAccountResponse;
import com.nguyenvu.lopet.account.dto.IdResponse;
import com.nguyenvu.lopet.account.entity.Account;
import com.nguyenvu.lopet.account.repository.AccountRepository;
import com.nguyenvu.lopet.common.exception.BadRequestException;
import com.nguyenvu.lopet.common.exception.NotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AccountService implements IAccountService {

    private final AccountRepository accountRepository;

    @Transactional(readOnly = true)
    public GetAccountResponse getById(Integer id) {
        Account account = accountRepository.findDetailById(id).orElseThrow(NotFoundException::new);
        return AccountMapper.toGetAccountResponse(account);
    }

    @Transactional(readOnly = true)
    public List<AccountViews.AccountListItem> getList() {
        return accountRepository.findAllDetail().stream()
                .map(AccountMapper::toListItem)
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

    @Override
    public long getTotalValidAccount() {
        return this.accountRepository.count();
    }
}

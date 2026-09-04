package com.nguyenvu.lopet.llms;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import com.nguyenvu.lopet.account.IAccountService;

@Component
public class AccountTool {
    private final IAccountService service;

    public AccountTool(IAccountService service) {
        this.service = service;
    }

    @Tool(description = "Look up how many valid accounts currently exist in the system")
    public Long getTotalValidAccount() {
        return this.service.getTotalValidAccount();
    }
}

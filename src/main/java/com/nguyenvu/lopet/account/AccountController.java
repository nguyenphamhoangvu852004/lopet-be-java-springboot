package com.nguyenvu.lopet.account;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nguyenvu.lopet.account.dto.AccountViews;
import com.nguyenvu.lopet.account.dto.GetAccountResponse;
import com.nguyenvu.lopet.account.dto.IdResponse;
import com.nguyenvu.lopet.account.dto.SetRolesRequest;
import com.nguyenvu.lopet.common.response.ApiResponse;
import com.nguyenvu.lopet.security.Auth;
import com.nguyenvu.lopet.security.CurrentUser;
import com.nguyenvu.lopet.security.RequirePermission;

import lombok.RequiredArgsConstructor;

@Tag(name = "Account management", description = "APIs for managing accounts")
@RestController
@RequestMapping("/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @Operation(summary = "Get all account",description = "Retrieve all account in system (included Admin account)")
    @GetMapping
    @Auth
    @RequirePermission("account:read")
    public ApiResponse<List<AccountViews.AccountListItem>> getList() {
        return ApiResponse.ok("Get list account successfully", accountService.getList());
    }

    @Operation(summary = "Get account information",description = "Get account information included Account Profile")
    @GetMapping("/{id}")
    @Auth
    public ApiResponse<GetAccountResponse> getById(@PathVariable Integer id) {
        return ApiResponse.ok("Get account successfully", accountService.getById(id));
    }

    @Operation(summary = "Do ban Account",description = "Set Account column isBanned to true")
    @PostMapping("/ban/{id}")
    @Auth
    @RequirePermission("account:ban")
    public ApiResponse<IdResponse> ban(@PathVariable Integer id) {
        return ApiResponse.ok("Ban account successfully", accountService.ban(id));
    }

    @Operation(summary = "Do unban Account",description = "Set Account column isBanned to false")
    @PostMapping("/unban/{id}")
    @Auth
    @RequirePermission("account:ban")
    public ApiResponse<IdResponse> unban(@PathVariable Integer id) {
        return ApiResponse.ok("Unban account successfully", accountService.unban(id));
    }

    @Operation(summary = "Hard delete one account",description = "This API will remove an account record in database")
    @DeleteMapping("/{id}")
    @Auth
    @RequirePermission("account:delete")
    public ApiResponse<IdResponse> delete(@PathVariable Integer id) {
        return ApiResponse.ok("Delete account successfully", accountService.delete(id));
    }

    @Operation(summary = "Get account suggestion",description = "This API return several accounts for purpose that send friendship, ...")
    @GetMapping("/suggest")
    @Auth
    public ApiResponse<List<AccountViews.AccountBrief>> getSuggest(@RequestParam(required = false) Integer limit) {
        return ApiResponse.ok("Get suggest account successfully",
                accountService.getSuggest(CurrentUser.require().id(), limit));
    }


    @Operation(summary = "Set role to specific account")
    @PutMapping
    @Auth
    @RequirePermission("account:setRole")
    public ApiResponse<AccountViews.AccountDetail> setRoles(@RequestBody SetRolesRequest request) {
        // grantedBy lấy từ token, không nhận từ body
        return ApiResponse.ok("Set roles to account successfully",
                accountService.setRoles(request.userId(), request.roles(), CurrentUser.require().id()));
    }
}

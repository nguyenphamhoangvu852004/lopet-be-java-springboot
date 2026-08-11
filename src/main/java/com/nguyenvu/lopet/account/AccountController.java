package com.nguyenvu.lopet.account;

import java.util.List;

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

@RestController
@RequestMapping("/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @GetMapping
    @Auth
    @RequirePermission("account:read")
    public ApiResponse<List<AccountViews.AccountListItem>> getList() {
        return ApiResponse.ok("Get list account successfully", accountService.getList());
    }

    /** Chỉ cần đăng nhập — bản TS không gắn requirePermission cho endpoint này */
    @GetMapping("/{id}")
    @Auth
    public ApiResponse<GetAccountResponse> getById(@PathVariable Integer id) {
        return ApiResponse.ok("Get account successfully", accountService.getById(id));
    }

    @PostMapping("/ban/{id}")
    @Auth
    @RequirePermission("account:ban")
    public ApiResponse<IdResponse> ban(@PathVariable Integer id) {
        return ApiResponse.ok("Ban account successfully", accountService.ban(id));
    }

    @PostMapping("/unban/{id}")
    @Auth
    @RequirePermission("account:ban")
    public ApiResponse<IdResponse> unban(@PathVariable Integer id) {
        return ApiResponse.ok("Unban account successfully", accountService.unban(id));
    }

    @DeleteMapping("/{id}")
    @Auth
    @RequirePermission("account:delete")
    public ApiResponse<IdResponse> delete(@PathVariable Integer id) {
        return ApiResponse.ok("Delete account successfully", accountService.delete(id));
    }

    /**
     * {@code :id} trên đường dẫn CỐ Ý bị bỏ qua — controller bên TS dùng {@code req.user.id}, nên
     * endpoint này luôn gợi ý cho chính người gọi. Giữ nguyên để URL cũ của client vẫn chạy.
     */
    @GetMapping("/suggest/{id}")
    @Auth
    public ApiResponse<List<AccountViews.AccountBrief>> getSuggest(@PathVariable Integer id,
                                                                   @RequestParam(required = false) Integer limit) {
        return ApiResponse.ok("Get suggest account successfully",
                accountService.getSuggest(CurrentUser.require().id(), limit));
    }

    /**
     * Endpoint ghi thẳng vào account_role. Trước bản vá bên TS chỉ có verifyToken nên bất kỳ ai
     * đăng nhập cũng tự gán ADMIN cho chính mình — quyền {@code account:setRole} là chốt chặn đó.
     */
    @PutMapping
    @Auth
    @RequirePermission("account:setRole")
    public ApiResponse<AccountViews.AccountDetail> setRoles(@RequestBody SetRolesRequest request) {
        // grantedBy lấy từ token, không nhận từ body
        return ApiResponse.ok("Set roles to account successfully",
                accountService.setRoles(request.userId(), request.roles(), CurrentUser.require().id()));
    }
}

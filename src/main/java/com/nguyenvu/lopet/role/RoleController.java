package com.nguyenvu.lopet.role;

import java.util.List;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nguyenvu.lopet.common.response.ApiResponse;
import com.nguyenvu.lopet.role.dto.RoleResponse;
import com.nguyenvu.lopet.role.entity.Role;
import com.nguyenvu.lopet.role.repository.RoleRepository;
import com.nguyenvu.lopet.security.Auth;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleRepository roleRepository;

    /** Chỉ cần đăng nhập, không cần permission — đúng như route TS */
    @GetMapping
    @Auth
    @Transactional(readOnly = true)
    public ApiResponse<List<RoleResponse>> getList() {
        List<RoleResponse> roles = roleRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
        return ApiResponse.ok("Get list role successfully", roles);
    }

    private RoleResponse toResponse(Role role) {
        return new RoleResponse(role.getId(), role.getName().name());
    }
}

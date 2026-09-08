package com.nguyenvu.lopet.bootstrap;


import com.nguyenvu.lopet.role.Role;
import com.nguyenvu.lopet.role.RoleRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class SeedDemoRoleAndPermission {
    private final RoleRepository roleRepository;

    public SeedDemoRoleAndPermission(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    void execute() {
        Role role1 = Role.builder()
                .id("ADMIN")
                .name("Admin")
                .build();
        Role role2 = Role.builder()
                .id("STAFF")
                .name("Staff")
                .build();
        this.roleRepository.saveAllAndFlush(new ArrayList<>(List.of(role1, role2)));
    }


}

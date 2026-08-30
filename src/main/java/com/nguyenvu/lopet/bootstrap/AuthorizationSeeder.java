package com.nguyenvu.lopet.bootstrap;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.nguyenvu.lopet.role.entity.Permission;
import com.nguyenvu.lopet.role.entity.Role;
import com.nguyenvu.lopet.role.entity.RoleName;
import com.nguyenvu.lopet.role.repository.PermissionRepository;
import com.nguyenvu.lopet.role.repository.RoleRepository;
import com.nguyenvu.lopet.security.authz.PermissionCatalog;
import com.nguyenvu.lopet.security.authz.PermissionDef;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthorizationSeeder {

    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;

    @Transactional
    public void execute() {
        for (PermissionDef definition : PermissionCatalog.ALL_PERMISSIONS) {
            Permission permission = permissionRepository.findByCode(definition.code())
                    .orElseGet(() -> Permission.builder().code(definition.code()).build());
            permission.setResource(definition.resource());
            permission.setAction(definition.action());
            permission.setScope(definition.scope());
            permission.setDescription(definition.description());
            permissionRepository.save(permission);
        }

        for (RoleName name : RoleName.values()) {
            if (roleRepository.findByName(name).isEmpty()) {
                roleRepository.save(Role.builder().name(name).build());
            }
        }

        List<Permission> allPermissions = permissionRepository.findAll();
        for (RoleName name : RoleName.values()) {
            Role role = roleRepository.findWithPermissionsByName(name).orElse(null);
            if (role == null) {
                continue;
            }
            List<String> codes = PermissionCatalog.ROLE_PERMISSIONS.getOrDefault(name, List.of());
            Set<Permission> granted = codes.contains(PermissionCatalog.WILDCARD)
                    ? new LinkedHashSet<>(allPermissions)
                    : allPermissions.stream()
                            .filter(permission -> codes.contains(permission.getCode()))
                            .collect(LinkedHashSet::new, Set::add, Set::addAll);
            role.setPermissions(granted);
            roleRepository.save(role);
        }

        log.info("Seed authorization xong: {} permissions, {} roles",
                PermissionCatalog.ALL_PERMISSIONS.size(), RoleName.values().length);
    }
}

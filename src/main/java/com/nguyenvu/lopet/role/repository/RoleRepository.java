package com.nguyenvu.lopet.role.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nguyenvu.lopet.role.entity.Role;
import com.nguyenvu.lopet.role.entity.RoleName;

public interface RoleRepository extends JpaRepository<Role, Integer> {

    Optional<Role> findByName(RoleName name);

    @EntityGraph(attributePaths = "permissions")
    @Query("select r from Role r where r.name = :name")
    Optional<Role> findWithPermissionsByName(@Param("name") RoleName name);
}

package com.nguyenvu.lopet.role;

import com.nguyenvu.lopet.account.entity.Account;
import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "roles")
public class Role {

    @Id
    private String id;

    private String name;

    @OneToMany(mappedBy = "role")
    private List<Account> account;

    @ManyToMany
    @JoinTable(
            name = "roles_permissions",
            joinColumns = {
                    @JoinColumn(name = "role_id")
            },
            inverseJoinColumns = {
                    @JoinColumn(name = "permission_id")
            }
    )
    private List<Permission> permissions;
}

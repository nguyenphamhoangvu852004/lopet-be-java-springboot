package com.nguyenvu.lopet.role;

import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "permissions")
public class Permission {
    @Id
    private String id;

    private String object;

    private String action;


    @ManyToMany(mappedBy = "permissions")
    private List<Role> roles;
}

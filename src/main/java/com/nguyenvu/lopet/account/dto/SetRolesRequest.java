package com.nguyenvu.lopet.account.dto;

import java.util.List;

/**
 * Body của endpoint nguy hiểm nhất hệ thống (PUT /v1/accounts) — nó ghi thẳng vào
 * {@code account_role}. Không có schema Joi bên TS nên cũng không thêm ràng buộc mới ở đây; chốt
 * chặn thật là quyền {@code account:setRole}.
 */
public record SetRolesRequest(Integer userId, List<String> roles) {
}

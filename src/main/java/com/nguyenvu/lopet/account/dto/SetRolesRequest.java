package com.nguyenvu.lopet.account.dto;

import java.util.List;

public record SetRolesRequest(Integer userId, List<String> roles) {
}

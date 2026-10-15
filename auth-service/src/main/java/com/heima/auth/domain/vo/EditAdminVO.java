package com.heima.auth.domain.vo;

import com.heima.api.enums.Role;
import lombok.Data;

@Data
public class EditAdminVO {
    private Long userId;
    private String username;
    private String nickname;
    private Role role;
}

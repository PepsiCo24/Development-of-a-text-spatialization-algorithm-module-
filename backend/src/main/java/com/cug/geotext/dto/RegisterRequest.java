package com.cug.geotext.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank(message = "请输入用户名")
    @Size(min = 3, max = 64, message = "用户名长度应为 3 至 64 个字符")
    @Pattern(regexp = "[A-Za-z0-9_-]+", message = "用户名仅支持字母、数字、下划线和短横线")
    String username,
    @NotBlank(message = "请输入显示名称")
    @Size(max = 100, message = "显示名称不能超过 100 个字符")
    String displayName,
    @NotBlank(message = "请输入密码")
    @Size(min = 8, max = 100, message = "密码长度至少为 8 个字符")
    String password
) {}

package com.cug.geotext.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cug.geotext.common.BusinessException;
import com.cug.geotext.dto.RegisterRequest;
import com.cug.geotext.entity.AppUser;
import com.cug.geotext.mapper.AppUserMapper;
import java.time.OffsetDateTime;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final AppUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(AppUserMapper userMapper, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public LoginResult login(String username, String password) {
        AppUser user = userMapper.selectOne(new LambdaQueryWrapper<AppUser>().eq(AppUser::getUsername, username));
        if (user == null || !Boolean.TRUE.equals(user.getEnabled()) || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BusinessException(401, "用户名或密码不正确");
        }
        return new LoginResult(jwtService.create(user.getUsername(), user.getRole()), "Bearer", user.getDisplayName(), user.getRole());
    }

    public LoginResult register(RegisterRequest request) {
        String username = request.username().trim();
        if (userMapper.selectCount(new LambdaQueryWrapper<AppUser>().eq(AppUser::getUsername, username)) > 0) {
            throw new BusinessException(409, "用户名已存在");
        }
        OffsetDateTime now = OffsetDateTime.now();
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setDisplayName(request.displayName().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole("USER");
        user.setEnabled(true);
        user.setCreateTime(now);
        user.setUpdateTime(now);
        userMapper.insert(user);
        return new LoginResult(jwtService.create(user.getUsername(), user.getRole()), "Bearer", user.getDisplayName(), user.getRole());
    }

    public record LoginResult(String accessToken, String tokenType, String displayName, String role) {}
}


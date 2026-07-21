package com.cug.geotext.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cug.geotext.common.BusinessException;
import com.cug.geotext.dto.RegisterRequest;
import com.cug.geotext.entity.AppUser;
import com.cug.geotext.mapper.AppUserMapper;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class AuthServiceTest {
    @Test
    void registersAnEnabledOrdinaryUserAndReturnsToken() {
        AppUserMapper mapper = mock(AppUserMapper.class);
        when(mapper.selectCount(any())).thenReturn(0L);
        AuthService service = new AuthService(mapper, new BCryptPasswordEncoder(), new JwtService(
            "GeoText-Local-2026-Change-Before-Production-9f13c8d7", Duration.ofHours(8)));

        AuthService.LoginResult result = service.register(new RegisterRequest("survey_user", "调查员", "password123"));

        ArgumentCaptor<AppUser> userCaptor = ArgumentCaptor.forClass(AppUser.class);
        verify(mapper).insert(userCaptor.capture());
        AppUser user = userCaptor.getValue();
        assertThat(user.getUsername()).isEqualTo("survey_user");
        assertThat(user.getDisplayName()).isEqualTo("调查员");
        assertThat(user.getRole()).isEqualTo("USER");
        assertThat(user.getEnabled()).isTrue();
        assertThat(new BCryptPasswordEncoder().matches("password123", user.getPasswordHash())).isTrue();
        assertThat(result.accessToken()).isNotBlank();
        assertThat(result.role()).isEqualTo("USER");
    }

    @Test
    void rejectsDuplicateUsername() {
        AppUserMapper mapper = mock(AppUserMapper.class);
        when(mapper.selectCount(any())).thenReturn(1L);
        AuthService service = new AuthService(mapper, new BCryptPasswordEncoder(), new JwtService(
            "GeoText-Local-2026-Change-Before-Production-9f13c8d7", Duration.ofHours(8)));

        assertThatThrownBy(() -> service.register(new RegisterRequest("admin", "重复用户", "password123")))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getCode())
            .isEqualTo(409);
    }
}

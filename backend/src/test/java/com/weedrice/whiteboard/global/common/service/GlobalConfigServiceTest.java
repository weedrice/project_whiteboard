package com.weedrice.whiteboard.global.common.service;

import com.weedrice.whiteboard.global.common.entity.GlobalConfig;
import com.weedrice.whiteboard.global.common.repository.GlobalConfigRepository;
import com.weedrice.whiteboard.global.common.util.SecurityUtils;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GlobalConfigServiceTest {

    @InjectMocks
    private GlobalConfigService globalConfigService;

    @Mock
    private GlobalConfigRepository globalConfigRepository;

    @Test
    @DisplayName("설정 조회 성공")
    void getConfig_success() {
        // given
        GlobalConfig config = new GlobalConfig("key", "value", "desc");
        when(globalConfigRepository.findById("key")).thenReturn(Optional.of(config));

        // when
        String value = globalConfigService.getConfig("key");

        // then
        assertThat(value).isEqualTo("value");
    }

    @Test
    @DisplayName("설정 생성 성공 (관리자 권한)")
    void createConfig_success() {
        try (MockedStatic<SecurityUtils> utilities = Mockito.mockStatic(SecurityUtils.class)) {
            // given
            utilities.when(SecurityUtils::validateSuperAdminPermission).thenAnswer(invocation -> null);
            when(globalConfigRepository.existsById(anyString())).thenReturn(false);
            when(globalConfigRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            // when
            GlobalConfig created = globalConfigService.createConfig("key", "value", "desc");

            // then
            assertThat(created.getConfigKey()).isEqualTo("key");
            assertThat(created.getConfigValue()).isEqualTo("value");
        }
    }

    @Test
    @DisplayName("설정 생성 실패 - 중복 (관리자 권한)")
    void createConfig_duplicate() {
        try (MockedStatic<SecurityUtils> utilities = Mockito.mockStatic(SecurityUtils.class)) {
            // given
            utilities.when(SecurityUtils::validateSuperAdminPermission).thenAnswer(invocation -> null);
            when(globalConfigRepository.existsById("key")).thenReturn(true);

            // when & then
            assertThatThrownBy(() -> globalConfigService.createConfig("key", "value", "desc"))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DUPLICATE_RESOURCE);
        }
    }

    @Test
    @DisplayName("설정 수정 성공 (관리자 권한)")
    void updateConfig_success() {
        try (MockedStatic<SecurityUtils> utilities = Mockito.mockStatic(SecurityUtils.class)) {
            // given
            GlobalConfig config = new GlobalConfig("key", "old", "old");
            utilities.when(SecurityUtils::validateSuperAdminPermission).thenAnswer(invocation -> null);
            when(globalConfigRepository.findById("key")).thenReturn(Optional.of(config));
            when(globalConfigRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            // when
            GlobalConfig updated = globalConfigService.updateConfig("key", "new", "new");

            // then
            assertThat(updated.getConfigValue()).isEqualTo("new");
        }
    }
}

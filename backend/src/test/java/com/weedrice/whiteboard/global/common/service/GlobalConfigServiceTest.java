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
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalConfigServiceTest {

    @InjectMocks
    private GlobalConfigService globalConfigService;

    @Mock
    private GlobalConfigRepository globalConfigRepository;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache cache;

    @Test
    @DisplayName("getConfig returns config value")
    void getConfig_success() {
        GlobalConfig config = new GlobalConfig("key", "value", "desc");
        when(globalConfigRepository.findById("key")).thenReturn(Optional.of(config));

        String value = globalConfigService.getConfig("key");

        assertThat(value).isEqualTo("value");
    }

    @Test
    @DisplayName("parseIntConfigOrDefault returns parsed integer")
    void parseIntConfigOrDefault_success() {
        int value = GlobalConfigService.parseIntConfigOrDefault(" 12 ", 10, 0);

        assertThat(value).isEqualTo(12);
    }

    @Test
    @DisplayName("parseIntConfigOrDefault falls back for invalid integer")
    void parseIntConfigOrDefault_invalidValue() {
        int value = GlobalConfigService.parseIntConfigOrDefault("invalid", 10, 0);

        assertThat(value).isEqualTo(10);
    }

    @Test
    @DisplayName("parseIntConfigOrDefault falls back below minimum")
    void parseIntConfigOrDefault_belowMinimum() {
        int value = GlobalConfigService.parseIntConfigOrDefault("-1", 10, 0);

        assertThat(value).isEqualTo(10);
    }

    @Test
    @DisplayName("createConfig saves and refreshes cache")
    void createConfig_success() {
        try (MockedStatic<SecurityUtils> utilities = Mockito.mockStatic(SecurityUtils.class)) {
            utilities.when(SecurityUtils::validateSuperAdminPermission).thenAnswer(invocation -> null);
            when(globalConfigRepository.existsById(anyString())).thenReturn(false);
            when(globalConfigRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
            when(cacheManager.getCache("globalConfig")).thenReturn(cache);

            GlobalConfig created = globalConfigService.createConfig("key", "value", "desc");

            assertThat(created.getConfigKey()).isEqualTo("key");
            assertThat(created.getConfigValue()).isEqualTo("value");
            verify(globalConfigRepository).saveAndFlush(any(GlobalConfig.class));
            verify(cache).put("key", "value");
        }
    }

    @Test
    @DisplayName("createConfig rejects duplicate found before save")
    void createConfig_duplicate() {
        try (MockedStatic<SecurityUtils> utilities = Mockito.mockStatic(SecurityUtils.class)) {
            utilities.when(SecurityUtils::validateSuperAdminPermission).thenAnswer(invocation -> null);
            when(globalConfigRepository.existsById("key")).thenReturn(true);

            assertThatThrownBy(() -> globalConfigService.createConfig("key", "value", "desc"))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DUPLICATE_RESOURCE);
        }
    }

    @Test
    @DisplayName("createConfig maps database duplicate constraint")
    void createConfig_duplicateFromDatabaseConstraint() {
        try (MockedStatic<SecurityUtils> utilities = Mockito.mockStatic(SecurityUtils.class)) {
            utilities.when(SecurityUtils::validateSuperAdminPermission).thenAnswer(invocation -> null);
            when(globalConfigRepository.existsById("key")).thenReturn(false);
            when(globalConfigRepository.saveAndFlush(any(GlobalConfig.class)))
                    .thenThrow(new DataIntegrityViolationException("duplicate key"));

            assertThatThrownBy(() -> globalConfigService.createConfig("key", "value", "desc"))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DUPLICATE_RESOURCE);
            verify(cacheManager, never()).getCache("globalConfig");
            verify(cache, never()).put(any(), any());
        }
    }

    @Test
    @DisplayName("updateConfig updates value")
    void updateConfig_success() {
        try (MockedStatic<SecurityUtils> utilities = Mockito.mockStatic(SecurityUtils.class)) {
            GlobalConfig config = new GlobalConfig("key", "old", "old");
            utilities.when(SecurityUtils::validateSuperAdminPermission).thenAnswer(invocation -> null);
            when(globalConfigRepository.findById("key")).thenReturn(Optional.of(config));
            when(globalConfigRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            GlobalConfig updated = globalConfigService.updateConfig("key", "new", "new");

            assertThat(updated.getConfigValue()).isEqualTo("new");
        }
    }
}

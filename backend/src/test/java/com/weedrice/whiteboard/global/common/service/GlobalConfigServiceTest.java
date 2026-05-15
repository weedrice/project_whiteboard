package com.weedrice.whiteboard.global.common.service;

import com.weedrice.whiteboard.global.common.dto.GlobalConfigResponse;
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
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
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
    @DisplayName("getConfig trims key before lookup")
    void getConfig_trimsKeyBeforeLookup() {
        GlobalConfig config = new GlobalConfig("key", "value", "desc");
        when(globalConfigRepository.findById("key")).thenReturn(Optional.of(config));

        String value = globalConfigService.getConfig(" key ");

        assertThat(value).isEqualTo("value");
    }

    @Test
    @DisplayName("getConfig returns null for missing config")
    void getConfig_missing_returnsNull() {
        when(globalConfigRepository.findById("key")).thenReturn(Optional.empty());

        String value = globalConfigService.getConfig("key");

        assertThat(value).isNull();
    }

    @Test
    @DisplayName("getConfigOrThrow returns config value")
    void getConfigOrThrow_success() {
        GlobalConfig config = new GlobalConfig("key", "value", "desc");
        when(globalConfigRepository.findById("key")).thenReturn(Optional.of(config));

        String value = globalConfigService.getConfigOrThrow("key");

        assertThat(value).isEqualTo("value");
    }

    @Test
    @DisplayName("getConfigOrThrow throws NOT_FOUND for missing config")
    void getConfigOrThrow_missing_throwsNotFound() {
        when(globalConfigRepository.findById("key")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> globalConfigService.getConfigOrThrow("key"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("getConfigResponseOrThrow returns normalized key and value")
    void getConfigResponseOrThrow_success() {
        GlobalConfig config = new GlobalConfig("key", "value", "desc");
        when(globalConfigRepository.findById("key")).thenReturn(Optional.of(config));

        GlobalConfigResponse response = globalConfigService.getConfigResponseOrThrow(" key ");

        assertThat(response.getKey()).isEqualTo("key");
        assertThat(response.getValue()).isEqualTo("value");
        assertThat(response.getDescription()).isNull();
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
    @DisplayName("getAllConfigs returns DTO list")
    void getAllConfigs_returnsResponses() {
        try (MockedStatic<SecurityUtils> utilities = Mockito.mockStatic(SecurityUtils.class)) {
            utilities.when(SecurityUtils::validateSuperAdminPermission).thenAnswer(invocation -> null);
            when(globalConfigRepository.findAll()).thenReturn(List.of(new GlobalConfig("key", "value", "desc")));

            var responses = globalConfigService.getAllConfigs();

            assertThat(responses).hasSize(1);
            assertThat(responses.getFirst().getKey()).isEqualTo("key");
            assertThat(responses.getFirst().getValue()).isEqualTo("value");
            assertThat(responses.getFirst().getDescription()).isEqualTo("desc");
        }
    }

    @Test
    @DisplayName("getPublicConfigs returns DTO list")
    void getPublicConfigs_returnsResponses() {
        when(globalConfigRepository.findByConfigKeyStartingWith("POINT_"))
                .thenReturn(List.of(new GlobalConfig("POINT_SIGNUP_BONUS", "10", "desc")));

        var responses = globalConfigService.getPublicConfigs();

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().getKey()).isEqualTo("POINT_SIGNUP_BONUS");
        assertThat(responses.getFirst().getValue()).isEqualTo("10");
    }

    @Test
    @DisplayName("createConfig saves and refreshes cache")
    void createConfig_success() {
        try (MockedStatic<SecurityUtils> utilities = Mockito.mockStatic(SecurityUtils.class)) {
            utilities.when(SecurityUtils::validateSuperAdminPermission).thenAnswer(invocation -> null);
            when(globalConfigRepository.existsById(anyString())).thenReturn(false);
            when(globalConfigRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
            when(cacheManager.getCache("globalConfig")).thenReturn(cache);

            GlobalConfigResponse created = globalConfigService.createConfig("key", "value", "desc");

            assertThat(created.getKey()).isEqualTo("key");
            assertThat(created.getValue()).isEqualTo("value");
            verify(globalConfigRepository).saveAndFlush(any(GlobalConfig.class));
            verify(cache).put("key", "value");
        }
    }

    @Test
    @DisplayName("createConfig trims key value and description consistently")
    void createConfig_trimsInputBeforeRepositoryAndCacheAccess() {
        try (MockedStatic<SecurityUtils> utilities = Mockito.mockStatic(SecurityUtils.class)) {
            utilities.when(SecurityUtils::validateSuperAdminPermission).thenAnswer(invocation -> null);
            when(globalConfigRepository.existsById("key")).thenReturn(false);
            when(globalConfigRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
            when(cacheManager.getCache("globalConfig")).thenReturn(cache);

            GlobalConfigResponse created = globalConfigService.createConfig(" key ", " value ", " desc ");

            assertThat(created.getKey()).isEqualTo("key");
            assertThat(created.getValue()).isEqualTo("value");
            assertThat(created.getDescription()).isEqualTo("desc");
            verify(globalConfigRepository).existsById("key");
            verify(cache).put("key", "value");
        }
    }

    @Test
    @DisplayName("createConfig refreshes cache after transaction commit when synchronization is active")
    void createConfig_activeTransactionSynchronization_refreshesCacheAfterCommit() {
        try (MockedStatic<SecurityUtils> utilities = Mockito.mockStatic(SecurityUtils.class)) {
            utilities.when(SecurityUtils::validateSuperAdminPermission).thenAnswer(invocation -> null);
            when(globalConfigRepository.existsById(anyString())).thenReturn(false);
            when(globalConfigRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
            when(cacheManager.getCache("globalConfig")).thenReturn(cache);

            TransactionSynchronizationManager.initSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(true);
            try {
                GlobalConfigResponse created = globalConfigService.createConfig("key", "value", "desc");

                assertThat(created.getKey()).isEqualTo("key");
                assertThat(created.getValue()).isEqualTo("value");
                verify(cache, never()).put(any(), any());

                TransactionSynchronizationManager.getSynchronizations()
                        .forEach(synchronization -> synchronization.afterCommit());
                verify(cache).put("key", "value");
            } finally {
                TransactionSynchronizationManager.setActualTransactionActive(false);
                TransactionSynchronizationManager.clearSynchronization();
            }
        }
    }

    @Test
    @DisplayName("createConfig ignores cache refresh failure after transaction commit")
    void createConfig_afterCommitCacheRefreshFails_doesNotThrow() {
        try (MockedStatic<SecurityUtils> utilities = Mockito.mockStatic(SecurityUtils.class)) {
            utilities.when(SecurityUtils::validateSuperAdminPermission).thenAnswer(invocation -> null);
            when(globalConfigRepository.existsById(anyString())).thenReturn(false);
            when(globalConfigRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
            when(cacheManager.getCache("globalConfig")).thenReturn(cache);

            TransactionSynchronizationManager.initSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(true);
            try {
                GlobalConfigResponse created = globalConfigService.createConfig("key", "value", "desc");
                when(cacheManager.getCache("globalConfig")).thenThrow(new IllegalStateException("cache down"));

                assertThat(created.getKey()).isEqualTo("key");
                TransactionSynchronizationManager.getSynchronizations()
                        .forEach(synchronization -> synchronization.afterCommit());
            } finally {
                TransactionSynchronizationManager.setActualTransactionActive(false);
                TransactionSynchronizationManager.clearSynchronization();
            }
        }
    }

    @Test
    @DisplayName("createConfig accepts non-negative point config values")
    void createConfig_pointConfig_acceptsNonNegativeInteger() {
        try (MockedStatic<SecurityUtils> utilities = Mockito.mockStatic(SecurityUtils.class)) {
            utilities.when(SecurityUtils::validateSuperAdminPermission).thenAnswer(invocation -> null);
            when(globalConfigRepository.existsById("POINT_SIGNUP_BONUS")).thenReturn(false);
            when(globalConfigRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
            when(cacheManager.getCache("globalConfig")).thenReturn(cache);

            GlobalConfigResponse created = globalConfigService.createConfig("POINT_SIGNUP_BONUS", "0", "desc");

            assertThat(created.getValue()).isEqualTo("0");
            verify(globalConfigRepository).saveAndFlush(any(GlobalConfig.class));
            verify(cache).put("POINT_SIGNUP_BONUS", "0");
        }
    }

    @Test
    @DisplayName("createConfig rejects invalid point config values")
    void createConfig_pointConfig_rejectsInvalidValue() {
        try (MockedStatic<SecurityUtils> utilities = Mockito.mockStatic(SecurityUtils.class)) {
            utilities.when(SecurityUtils::validateSuperAdminPermission).thenAnswer(invocation -> null);

            assertThatThrownBy(() -> globalConfigService.createConfig("POINT_SIGNUP_BONUS", "invalid", "desc"))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);
            assertThatThrownBy(() -> globalConfigService.createConfig("POINT_POST_CREATE_REWARD", "-1", "desc"))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);
            assertThatThrownBy(() -> globalConfigService.createConfig(" POINT_SIGNUP_BONUS ", "invalid", "desc"))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);

            verify(globalConfigRepository, never()).existsById(anyString());
            verify(globalConfigRepository, never()).saveAndFlush(any());
            verify(cacheManager, never()).getCache("globalConfig");
        }
    }

    @Test
    @DisplayName("createConfig rejects invalid key value and description before repository access")
    void createConfig_invalidText_rejectsBeforeRepositoryAccess() {
        try (MockedStatic<SecurityUtils> utilities = Mockito.mockStatic(SecurityUtils.class)) {
            utilities.when(SecurityUtils::validateSuperAdminPermission).thenAnswer(invocation -> null);

            assertThatThrownBy(() -> globalConfigService.createConfig(null, "value", "desc"))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);
            assertThatThrownBy(() -> globalConfigService.createConfig("   ", "value", "desc"))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);
            assertThatThrownBy(() -> globalConfigService.createConfig("k".repeat(101), "value", "desc"))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);
            assertThatThrownBy(() -> globalConfigService.createConfig("key", "   ", "desc"))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);
            assertThatThrownBy(() -> globalConfigService.createConfig("key", "v".repeat(256), "desc"))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);
            assertThatThrownBy(() -> globalConfigService.createConfig("key", "value", "d".repeat(256)))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);

            verify(globalConfigRepository, never()).existsById(any());
            verify(globalConfigRepository, never()).saveAndFlush(any());
            verify(cacheManager, never()).getCache("globalConfig");
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

            GlobalConfigResponse updated = globalConfigService.updateConfig("key", "new", "new");

            assertThat(updated.getValue()).isEqualTo("new");
        }
    }

    @Test
    @DisplayName("updateConfig trims key value and description consistently")
    void updateConfig_trimsInputBeforeRepositoryAndCacheAccess() {
        try (MockedStatic<SecurityUtils> utilities = Mockito.mockStatic(SecurityUtils.class)) {
            GlobalConfig config = new GlobalConfig("key", "old", "old");
            utilities.when(SecurityUtils::validateSuperAdminPermission).thenAnswer(invocation -> null);
            when(globalConfigRepository.findById("key")).thenReturn(Optional.of(config));
            when(globalConfigRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
            when(cacheManager.getCache("globalConfig")).thenReturn(cache);

            GlobalConfigResponse updated = globalConfigService.updateConfig(" key ", " new ", " desc ");

            assertThat(updated.getValue()).isEqualTo("new");
            assertThat(updated.getDescription()).isEqualTo("desc");
            verify(globalConfigRepository).findById("key");
            verify(cache).put("key", "new");
        }
    }

    @Test
    @DisplayName("updateConfig refreshes cache after transaction commit when synchronization is active")
    void updateConfig_activeTransactionSynchronization_refreshesCacheAfterCommit() {
        try (MockedStatic<SecurityUtils> utilities = Mockito.mockStatic(SecurityUtils.class)) {
            GlobalConfig config = new GlobalConfig("key", "old", "old");
            utilities.when(SecurityUtils::validateSuperAdminPermission).thenAnswer(invocation -> null);
            when(globalConfigRepository.findById("key")).thenReturn(Optional.of(config));
            when(globalConfigRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
            when(cacheManager.getCache("globalConfig")).thenReturn(cache);

            TransactionSynchronizationManager.initSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(true);
            try {
                GlobalConfigResponse updated = globalConfigService.updateConfig("key", "new", "new");

                assertThat(updated.getValue()).isEqualTo("new");
                verify(cache, never()).put(any(), any());

                TransactionSynchronizationManager.getSynchronizations()
                        .forEach(synchronization -> synchronization.afterCommit());
                verify(cache).put("key", "new");
            } finally {
                TransactionSynchronizationManager.setActualTransactionActive(false);
                TransactionSynchronizationManager.clearSynchronization();
            }
        }
    }

    @Test
    @DisplayName("deleteConfig evicts cache after transaction commit when synchronization is active")
    void deleteConfig_activeTransactionSynchronization_evictsCacheAfterCommit() {
        try (MockedStatic<SecurityUtils> utilities = Mockito.mockStatic(SecurityUtils.class)) {
            utilities.when(SecurityUtils::validateSuperAdminPermission).thenAnswer(invocation -> null);
            when(globalConfigRepository.existsById("key")).thenReturn(true);
            when(cacheManager.getCache("globalConfig")).thenReturn(cache);

            TransactionSynchronizationManager.initSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(true);
            try {
                globalConfigService.deleteConfig("key");

                verify(cache, never()).evict(any());
                verify(globalConfigRepository).deleteById("key");

                TransactionSynchronizationManager.getSynchronizations()
                        .forEach(synchronization -> synchronization.afterCommit());
                verify(cache).evict("key");
            } finally {
                TransactionSynchronizationManager.setActualTransactionActive(false);
                TransactionSynchronizationManager.clearSynchronization();
            }
        }
    }

    @Test
    @DisplayName("updateConfig rejects invalid key value and description before repository access")
    void updateConfig_invalidText_rejectsBeforeRepositoryAccess() {
        try (MockedStatic<SecurityUtils> utilities = Mockito.mockStatic(SecurityUtils.class)) {
            utilities.when(SecurityUtils::validateSuperAdminPermission).thenAnswer(invocation -> null);

            assertThatThrownBy(() -> globalConfigService.updateConfig(null, "value", "desc"))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);
            assertThatThrownBy(() -> globalConfigService.updateConfig("   ", "value", "desc"))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);
            assertThatThrownBy(() -> globalConfigService.updateConfig("k".repeat(101), "value", "desc"))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);
            assertThatThrownBy(() -> globalConfigService.updateConfig("key", null, "desc"))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);
            assertThatThrownBy(() -> globalConfigService.updateConfig("key", "v".repeat(256), "desc"))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);
            assertThatThrownBy(() -> globalConfigService.updateConfig("key", "value", "d".repeat(256)))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);

            verify(globalConfigRepository, never()).findById(anyString());
            verify(globalConfigRepository, never()).save(any());
        }
    }

    @Test
    @DisplayName("updateConfig rejects invalid point config values")
    void updateConfig_pointConfig_rejectsInvalidValue() {
        try (MockedStatic<SecurityUtils> utilities = Mockito.mockStatic(SecurityUtils.class)) {
            utilities.when(SecurityUtils::validateSuperAdminPermission).thenAnswer(invocation -> null);

            assertThatThrownBy(() -> globalConfigService.updateConfig("POINT_BOARD_CREATE_COST", "invalid", "desc"))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);
            assertThatThrownBy(() -> globalConfigService.updateConfig("POINT_BOARD_CREATE_COST", "-1", "desc"))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);

            verify(globalConfigRepository, never()).findById(anyString());
            verify(globalConfigRepository, never()).save(any());
        }
    }
}

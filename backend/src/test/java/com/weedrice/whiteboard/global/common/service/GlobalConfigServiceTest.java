package com.weedrice.whiteboard.global.common.service;

import com.weedrice.whiteboard.global.common.dto.GlobalConfigResponse;
import com.weedrice.whiteboard.global.common.entity.GlobalConfig;
import com.weedrice.whiteboard.global.common.repository.GlobalConfigRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import com.weedrice.whiteboard.global.security.SuperAdminPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalConfigServiceTest {

    private static final Long ACTOR_USER_ID = 1L;

    private GlobalConfigService globalConfigService;

    @Mock
    private GlobalConfigRepository globalConfigRepository;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache cache;

    @Mock
    private SuperAdminPolicy superAdminPolicy;

    @BeforeEach
    void setUp() {
        globalConfigService = new GlobalConfigService(
                globalConfigRepository,
                cacheManager,
                superAdminPolicy,
                new GlobalConfigDuplicatePolicy(globalConfigRepository));
    }

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
    @DisplayName("getConfigFresh reads the repository without cache access")
    void getConfigFresh_readsRepositoryDirectly() {
        GlobalConfig config = new GlobalConfig("key", "fresh", "desc");
        when(globalConfigRepository.findById("key")).thenReturn(Optional.of(config));

        String value = globalConfigService.getConfigFresh(" key ");

        assertThat(value).isEqualTo("fresh");
        verify(globalConfigRepository).findById("key");
        verifyNoInteractions(cacheManager);
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

        GlobalConfigResponse response = globalConfigService.getConfigResponseOrThrow(ACTOR_USER_ID, " key ");

        assertThat(response.getKey()).isEqualTo("key");
        assertThat(response.getValue()).isEqualTo("value");
        assertThat(response.getDescription()).isNull();
        verify(superAdminPolicy).requireUsableSuperAdmin(ACTOR_USER_ID);
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
    @DisplayName("bounded integer config parser accepts only values within both boundaries")
    void parseIntConfigOrDefault_boundedRange() {
        assertThat(GlobalConfigService.parseIntConfigOrDefault("1", 20, 1, 100)).isEqualTo(1);
        assertThat(GlobalConfigService.parseIntConfigOrDefault("100", 20, 1, 100)).isEqualTo(100);
        assertThat(GlobalConfigService.parseIntConfigOrDefault("0", 20, 1, 100)).isEqualTo(20);
        assertThat(GlobalConfigService.parseIntConfigOrDefault("101", 20, 1, 100)).isEqualTo(20);
    }

    @Test
    @DisplayName("getAllConfigs returns DTO list")
    void getAllConfigs_returnsResponses() {
        when(globalConfigRepository.findAll()).thenReturn(List.of(new GlobalConfig("key", "value", "desc")));

        var responses = globalConfigService.getAllConfigs(ACTOR_USER_ID);

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().getKey()).isEqualTo("key");
        assertThat(responses.getFirst().getValue()).isEqualTo("value");
        assertThat(responses.getFirst().getDescription()).isEqualTo("desc");
        verify(superAdminPolicy).requireUsableSuperAdmin(ACTOR_USER_ID);
    }

    @Test
    @DisplayName("getAllConfigs stops before repository access when super admin policy rejects")
    void getAllConfigs_guardRejects_stopsBeforeRepositoryAccess() {
        doThrow(new BusinessException(ErrorCode.FORBIDDEN))
                .when(superAdminPolicy).requireUsableSuperAdmin(ACTOR_USER_ID);

        assertThatThrownBy(() -> globalConfigService.getAllConfigs(ACTOR_USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
        verify(globalConfigRepository, never()).findAll();
    }

    @Test
    @DisplayName("getPublicConfigs returns DTO list")
    void getPublicConfigs_returnsResponses() {
        when(globalConfigRepository.findByConfigKeyStartingWith("POINT_"))
                .thenReturn(List.of(new GlobalConfig("POINT_SIGNUP_BONUS", "10", "desc")));
        when(globalConfigRepository.findAllById(List.of(GlobalConfigService.EMOTICON_IMAGE_MAX_COUNT_CONFIG_KEY)))
                .thenReturn(List.of(new GlobalConfig(
                        GlobalConfigService.EMOTICON_IMAGE_MAX_COUNT_CONFIG_KEY,
                        "20",
                        "emoticon limit")));

        var responses = globalConfigService.getPublicConfigs();

        assertThat(responses)
                .extracting(GlobalConfigResponse::getKey)
                .containsExactly(
                        "POINT_SIGNUP_BONUS",
                        GlobalConfigService.EMOTICON_IMAGE_MAX_COUNT_CONFIG_KEY);
        verify(globalConfigRepository, never()).findByConfigKeyStartingWith("EMOTICON_");
        verifyNoInteractions(superAdminPolicy);
    }

    @Test
    @DisplayName("createConfig saves and refreshes cache")
    void createConfig_success() {
        when(globalConfigRepository.existsById(anyString())).thenReturn(false);
        when(globalConfigRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(cacheManager.getCache("globalConfig")).thenReturn(cache);

        GlobalConfigResponse created = globalConfigService.createConfig(ACTOR_USER_ID, "key", "value", "desc");

        assertThat(created.getKey()).isEqualTo("key");
        assertThat(created.getValue()).isEqualTo("value");
        verify(globalConfigRepository).saveAndFlush(any(GlobalConfig.class));
        verify(cache).put("key", "value");
        verify(superAdminPolicy).requireUsableSuperAdmin(ACTOR_USER_ID);
    }

    @Test
    @DisplayName("createConfig trims key value and description consistently")
    void createConfig_trimsInputBeforeRepositoryAndCacheAccess() {
        when(globalConfigRepository.existsById("key")).thenReturn(false);
        when(globalConfigRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(cacheManager.getCache("globalConfig")).thenReturn(cache);

        GlobalConfigResponse created = globalConfigService.createConfig(ACTOR_USER_ID, " key ", " value ", " desc ");

        assertThat(created.getKey()).isEqualTo("key");
        assertThat(created.getValue()).isEqualTo("value");
        assertThat(created.getDescription()).isEqualTo("desc");
        verify(globalConfigRepository).existsById("key");
        verify(cache).put("key", "value");
    }

    @Test
    @DisplayName("createConfig refreshes cache after transaction commit when synchronization is active")
    void createConfig_activeTransactionSynchronization_refreshesCacheAfterCommit() {
        when(globalConfigRepository.existsById(anyString())).thenReturn(false);
        when(globalConfigRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(cacheManager.getCache("globalConfig")).thenReturn(cache);

        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            GlobalConfigResponse created = globalConfigService.createConfig(ACTOR_USER_ID, "key", "value", "desc");

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

    @Test
    @DisplayName("createConfig ignores cache refresh failure after transaction commit")
    void createConfig_afterCommitCacheRefreshFails_doesNotThrow() {
        when(globalConfigRepository.existsById(anyString())).thenReturn(false);
        when(globalConfigRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(cacheManager.getCache("globalConfig")).thenReturn(cache);

        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            GlobalConfigResponse created = globalConfigService.createConfig(ACTOR_USER_ID, "key", "value", "desc");
            when(cacheManager.getCache("globalConfig")).thenThrow(new IllegalStateException("cache down"));

            assertThat(created.getKey()).isEqualTo("key");
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(synchronization -> synchronization.afterCommit());
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("createConfig accepts non-negative point config values")
    void createConfig_pointConfig_acceptsNonNegativeInteger() {
        when(globalConfigRepository.existsById("POINT_SIGNUP_BONUS")).thenReturn(false);
        when(globalConfigRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(cacheManager.getCache("globalConfig")).thenReturn(cache);

        GlobalConfigResponse created = globalConfigService.createConfig(
                ACTOR_USER_ID,
                "POINT_SIGNUP_BONUS",
                "0",
                "desc");

        assertThat(created.getValue()).isEqualTo("0");
        verify(globalConfigRepository).saveAndFlush(any(GlobalConfig.class));
        verify(cache).put("POINT_SIGNUP_BONUS", "0");
    }

    @Test
    @DisplayName("createConfig rejects invalid point config values")
    void createConfig_pointConfig_rejectsInvalidValue() {
        assertThatThrownBy(() -> globalConfigService.createConfig(ACTOR_USER_ID, "POINT_SIGNUP_BONUS", "invalid", "desc"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);
        assertThatThrownBy(() -> globalConfigService.createConfig(
                ACTOR_USER_ID,
                "POINT_POST_CREATE_REWARD",
                "-1",
                "desc"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);
        assertThatThrownBy(() -> globalConfigService.createConfig(
                ACTOR_USER_ID,
                " POINT_SIGNUP_BONUS ",
                "invalid",
                "desc"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);

        verify(globalConfigRepository, never()).existsById(anyString());
        verify(globalConfigRepository, never()).saveAndFlush(any());
        verify(cacheManager, never()).getCache("globalConfig");
    }

    @Test
    @DisplayName("NOBICON_PRICE는 0 이상의 정수만 허용한다")
    void createConfig_nobiconPrice_validatesNonNegativeInteger() {
        when(globalConfigRepository.existsById(GlobalConfigService.NOBICON_PRICE_CONFIG_KEY)).thenReturn(false);
        when(globalConfigRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        GlobalConfigResponse created = globalConfigService.createConfig(
                ACTOR_USER_ID,
                GlobalConfigService.NOBICON_PRICE_CONFIG_KEY,
                "0",
                "desc");

        assertThat(created.getValue()).isEqualTo("0");

        for (String invalidValue : List.of("-1", "1.5", "invalid")) {
            assertThatThrownBy(() -> globalConfigService.createConfig(
                    ACTOR_USER_ID,
                    GlobalConfigService.NOBICON_PRICE_CONFIG_KEY,
                    invalidValue,
                    "desc"))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    @Test
    @DisplayName("createConfig accepts emoticon image limits from 1 through 100")
    void createConfig_emoticonImageLimit_acceptsBoundaries() {
        when(globalConfigRepository.existsById(GlobalConfigService.EMOTICON_IMAGE_MAX_COUNT_CONFIG_KEY))
                .thenReturn(false);
        when(globalConfigRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        GlobalConfigResponse created = globalConfigService.createConfig(
                ACTOR_USER_ID,
                GlobalConfigService.EMOTICON_IMAGE_MAX_COUNT_CONFIG_KEY,
                "1",
                "desc");

        assertThat(created.getValue()).isEqualTo("1");
    }

    @Test
    @DisplayName("createConfig rejects emoticon image limits outside 1 through 100")
    void createConfig_emoticonImageLimit_rejectsInvalidValues() {
        assertThatThrownBy(() -> globalConfigService.createConfig(
                ACTOR_USER_ID,
                GlobalConfigService.EMOTICON_IMAGE_MAX_COUNT_CONFIG_KEY,
                "0",
                "desc"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);
        assertThatThrownBy(() -> globalConfigService.createConfig(
                ACTOR_USER_ID,
                GlobalConfigService.EMOTICON_IMAGE_MAX_COUNT_CONFIG_KEY,
                "101",
                "desc"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);
        assertThatThrownBy(() -> globalConfigService.createConfig(
                ACTOR_USER_ID,
                GlobalConfigService.EMOTICON_IMAGE_MAX_COUNT_CONFIG_KEY,
                "invalid",
                "desc"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);

        verify(globalConfigRepository, never()).existsById(anyString());
        verify(globalConfigRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("createConfig rejects invalid key value and description before repository access")
    void createConfig_invalidText_rejectsBeforeRepositoryAccess() {
        assertThatThrownBy(() -> globalConfigService.createConfig(ACTOR_USER_ID, null, "value", "desc"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);
        assertThatThrownBy(() -> globalConfigService.createConfig(ACTOR_USER_ID, "   ", "value", "desc"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);
        assertThatThrownBy(() -> globalConfigService.createConfig(ACTOR_USER_ID, "k".repeat(101), "value", "desc"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);
        assertThatThrownBy(() -> globalConfigService.createConfig(ACTOR_USER_ID, "key", "   ", "desc"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);
        assertThatThrownBy(() -> globalConfigService.createConfig(ACTOR_USER_ID, "key", "v".repeat(10_001), "desc"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);
        assertThatThrownBy(() -> globalConfigService.createConfig(ACTOR_USER_ID, "key", "value", "d".repeat(256)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);

        verify(globalConfigRepository, never()).existsById(any());
        verify(globalConfigRepository, never()).saveAndFlush(any());
        verify(cacheManager, never()).getCache("globalConfig");
    }

    @Test
    @DisplayName("createConfig rejects duplicate found before save")
    void createConfig_duplicate() {
        when(globalConfigRepository.existsById("key")).thenReturn(true);

        assertThatThrownBy(() -> globalConfigService.createConfig(ACTOR_USER_ID, "key", "value", "desc"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DUPLICATE_RESOURCE);
    }

    @Test
    @DisplayName("createConfig maps database duplicate constraint")
    void createConfig_duplicateFromDatabaseConstraint() {
        when(globalConfigRepository.existsById("key")).thenReturn(false);
        when(globalConfigRepository.saveAndFlush(any(GlobalConfig.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> globalConfigService.createConfig(ACTOR_USER_ID, "key", "value", "desc"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DUPLICATE_RESOURCE);
        verify(cacheManager, never()).getCache("globalConfig");
        verify(cache, never()).put(any(), any());
    }

    @Test
    @DisplayName("updateConfig updates value")
    void updateConfig_success() {
        GlobalConfig config = new GlobalConfig("key", "old", "old");
        when(globalConfigRepository.findById("key")).thenReturn(Optional.of(config));
        when(globalConfigRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        GlobalConfigResponse updated = globalConfigService.updateConfig(ACTOR_USER_ID, "key", "new", "new");

        assertThat(updated.getValue()).isEqualTo("new");
        verify(superAdminPolicy).requireUsableSuperAdmin(ACTOR_USER_ID);
    }

    @Test
    @DisplayName("updateConfig trims key value and description consistently")
    void updateConfig_trimsInputBeforeRepositoryAndCacheAccess() {
        GlobalConfig config = new GlobalConfig("key", "old", "old");
        when(globalConfigRepository.findById("key")).thenReturn(Optional.of(config));
        when(globalConfigRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(cacheManager.getCache("globalConfig")).thenReturn(cache);

        GlobalConfigResponse updated = globalConfigService.updateConfig(ACTOR_USER_ID, " key ", " new ", " desc ");

        assertThat(updated.getValue()).isEqualTo("new");
        assertThat(updated.getDescription()).isEqualTo("desc");
        verify(globalConfigRepository).findById("key");
        verify(cache).put("key", "new");
    }

    @Test
    @DisplayName("updateConfig refreshes cache after transaction commit when synchronization is active")
    void updateConfig_activeTransactionSynchronization_refreshesCacheAfterCommit() {
        GlobalConfig config = new GlobalConfig("key", "old", "old");
        when(globalConfigRepository.findById("key")).thenReturn(Optional.of(config));
        when(globalConfigRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(cacheManager.getCache("globalConfig")).thenReturn(cache);

        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            GlobalConfigResponse updated = globalConfigService.updateConfig(ACTOR_USER_ID, "key", "new", "new");

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

    @Test
    @DisplayName("deleteConfig evicts cache after transaction commit when synchronization is active")
    void deleteConfig_activeTransactionSynchronization_evictsCacheAfterCommit() {
        when(globalConfigRepository.existsById("key")).thenReturn(true);
        when(cacheManager.getCache("globalConfig")).thenReturn(cache);

        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            globalConfigService.deleteConfig(ACTOR_USER_ID, "key");

            verify(cache, never()).evict(any());
            verify(globalConfigRepository).deleteById("key");
            verify(superAdminPolicy).requireUsableSuperAdmin(ACTOR_USER_ID);

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(synchronization -> synchronization.afterCommit());
            verify(cache).evict("key");
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("updateConfig rejects invalid key value and description before repository access")
    void updateConfig_invalidText_rejectsBeforeRepositoryAccess() {
        assertThatThrownBy(() -> globalConfigService.updateConfig(ACTOR_USER_ID, null, "value", "desc"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);
        assertThatThrownBy(() -> globalConfigService.updateConfig(ACTOR_USER_ID, "   ", "value", "desc"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);
        assertThatThrownBy(() -> globalConfigService.updateConfig(ACTOR_USER_ID, "k".repeat(101), "value", "desc"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);
        assertThatThrownBy(() -> globalConfigService.updateConfig(ACTOR_USER_ID, "key", null, "desc"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);
        assertThatThrownBy(() -> globalConfigService.updateConfig(ACTOR_USER_ID, "key", "v".repeat(10_001), "desc"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);
        assertThatThrownBy(() -> globalConfigService.updateConfig(ACTOR_USER_ID, "key", "value", "d".repeat(256)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);

        verify(globalConfigRepository, never()).findById(anyString());
        verify(globalConfigRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateConfig rejects invalid point config values")
    void updateConfig_pointConfig_rejectsInvalidValue() {
        assertThatThrownBy(() -> globalConfigService.updateConfig(
                ACTOR_USER_ID,
                "POINT_BOARD_CREATE_COST",
                "invalid",
                "desc"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);
        assertThatThrownBy(() -> globalConfigService.updateConfig(
                ACTOR_USER_ID,
                "POINT_BOARD_CREATE_COST",
                "-1",
                "desc"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);

        verify(globalConfigRepository, never()).findById(anyString());
        verify(globalConfigRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateConfig accepts the absolute emoticon image limit")
    void updateConfig_emoticonImageLimit_acceptsAbsoluteMaximum() {
        GlobalConfig config = new GlobalConfig(
                GlobalConfigService.EMOTICON_IMAGE_MAX_COUNT_CONFIG_KEY,
                "20",
                "desc");
        when(globalConfigRepository.findById(GlobalConfigService.EMOTICON_IMAGE_MAX_COUNT_CONFIG_KEY))
                .thenReturn(Optional.of(config));
        when(globalConfigRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        GlobalConfigResponse updated = globalConfigService.updateConfig(
                ACTOR_USER_ID,
                GlobalConfigService.EMOTICON_IMAGE_MAX_COUNT_CONFIG_KEY,
                "100",
                "desc");

        assertThat(updated.getValue()).isEqualTo("100");
    }

    @Test
    @DisplayName("updateConfig rejects invalid emoticon image limits")
    void updateConfig_emoticonImageLimit_rejectsInvalidValues() {
        for (String invalidValue : List.of("0", "101", "1.5", "invalid")) {
            assertThatThrownBy(() -> globalConfigService.updateConfig(
                    ACTOR_USER_ID,
                    GlobalConfigService.EMOTICON_IMAGE_MAX_COUNT_CONFIG_KEY,
                    invalidValue,
                    "desc"))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);
        }

        verify(globalConfigRepository, never()).findById(anyString());
        verify(globalConfigRepository, never()).save(any());
    }
}

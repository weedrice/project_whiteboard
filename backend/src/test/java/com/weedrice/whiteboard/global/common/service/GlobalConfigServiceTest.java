package com.weedrice.whiteboard.global.common.service;

import com.weedrice.whiteboard.global.common.entity.GlobalConfig;
import com.weedrice.whiteboard.global.common.repository.GlobalConfigRepository;
import com.weedrice.whiteboard.global.common.util.SecurityUtils;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GlobalConfigServiceTest {

    @Mock
    private GlobalConfigRepository globalConfigRepository;

    @InjectMocks
    private GlobalConfigService globalConfigService;

    private MockedStatic<SecurityUtils> securityUtilsMockedStatic;

    @BeforeEach
    void setUp() {
        securityUtilsMockedStatic = mockStatic(SecurityUtils.class);
    }

    @AfterEach
    void tearDown() {
        securityUtilsMockedStatic.close();
    }

    @Test
    @DisplayName("설정 조회 성공")
    void getConfig_success() {
        GlobalConfig config = new GlobalConfig("key", "value", "desc");
        when(globalConfigRepository.findById("key")).thenReturn(Optional.of(config));

        String value = globalConfigService.getConfig("key");

        assertThat(value).isEqualTo("value");
    }

    @Test
    @DisplayName("설정 조회 실패 - 존재하지 않음")
    void getConfig_notFound() {
        when(globalConfigRepository.findById("key")).thenReturn(Optional.empty());

        String value = globalConfigService.getConfig("key");

        assertThat(value).isNull();
    }

    @Test
    @DisplayName("전체 설정 조회 - 권한 검증")
    void getAllConfigs_success() {
        // given
        when(globalConfigRepository.findAll()).thenReturn(Collections.emptyList());

        // when
        List<GlobalConfig> configs = globalConfigService.getAllConfigs();

        // then
        assertThat(configs).isEmpty();
        securityUtilsMockedStatic.verify(SecurityUtils::validateSuperAdminPermission);
    }

    @Test
    @DisplayName("설정 생성 성공")
    void createConfig_success() {
        // given
        when(globalConfigRepository.existsById("key")).thenReturn(false);
        when(globalConfigRepository.save(any(GlobalConfig.class))).thenAnswer(i -> i.getArgument(0));

        // when
        GlobalConfig created = globalConfigService.createConfig("key", "value", "desc");

        // then
        assertThat(created.getConfigKey()).isEqualTo("key");
        assertThat(created.getConfigValue()).isEqualTo("value");
        securityUtilsMockedStatic.verify(SecurityUtils::validateSuperAdminPermission);
    }

    @Test
    @DisplayName("설정 생성 실패 - 중복 키")
    void createConfig_duplicate() {
        // given
        when(globalConfigRepository.existsById("key")).thenReturn(true);

        // when & then
        assertThatThrownBy(() -> globalConfigService.createConfig("key", "value", "desc"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DUPLICATE_RESOURCE);
    }

    @Test
    @DisplayName("설정 수정 성공")
    void updateConfig_success() {
        // given
        GlobalConfig config = new GlobalConfig("key", "old", "desc");
        when(globalConfigRepository.findById("key")).thenReturn(Optional.of(config));
        when(globalConfigRepository.save(any(GlobalConfig.class))).thenAnswer(i -> i.getArgument(0));

        // when
        GlobalConfig updated = globalConfigService.updateConfig("key", "new", "desc");

        // then
        assertThat(updated.getConfigValue()).isEqualTo("new");
        securityUtilsMockedStatic.verify(SecurityUtils::validateSuperAdminPermission);
    }

    @Test
    @DisplayName("설정 수정 실패 - 존재하지 않음")
    void updateConfig_notFound() {
        // given
        when(globalConfigRepository.findById("key")).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> globalConfigService.updateConfig("key", "val", "desc"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("설정 삭제 성공")
    void deleteConfig_success() {
        // given
        when(globalConfigRepository.existsById("key")).thenReturn(true);

        // when
        globalConfigService.deleteConfig("key");

        // then
        verify(globalConfigRepository).deleteById("key");
        securityUtilsMockedStatic.verify(SecurityUtils::validateSuperAdminPermission);
    }

    @Test
    @DisplayName("설정 삭제 실패 - 존재하지 않음")
    void deleteConfig_notFound() {
        // given
        when(globalConfigRepository.existsById("key")).thenReturn(false);

        // when & then
        assertThatThrownBy(() -> globalConfigService.deleteConfig("key"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_FOUND);
    }
}
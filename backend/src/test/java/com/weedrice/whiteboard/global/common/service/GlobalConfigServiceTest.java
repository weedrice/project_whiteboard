package com.weedrice.whiteboard.global.common.service;

import com.weedrice.whiteboard.global.common.entity.GlobalConfig;
import com.weedrice.whiteboard.global.common.repository.GlobalConfigRepository;
import com.weedrice.whiteboard.global.common.util.SecurityUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalConfigServiceTest {

    @Mock
    private GlobalConfigRepository globalConfigRepository;

    @InjectMocks
    private GlobalConfigService globalConfigService;

    private MockedStatic<SecurityUtils> mockedSecurityUtils;

    @BeforeEach
    void setUp() {
        mockedSecurityUtils = mockStatic(SecurityUtils.class);
    }

    @AfterEach
    void tearDown() {
        mockedSecurityUtils.close();
    }

    @Test
    @DisplayName("설정 조회 성공")
    void getConfig_success() {
        // given
        String key = "TEST_KEY";
        GlobalConfig config = new GlobalConfig(key, "TEST_VALUE", "Description");
        when(globalConfigRepository.findById(key)).thenReturn(Optional.of(config));

        // when
        String value = globalConfigService.getConfig(key);

        // then
        assertThat(value).isEqualTo("TEST_VALUE");
    }

    @Test
    @DisplayName("설정 수정 성공")
    void updateConfig_success() {
        // given
        String key = "TEST_KEY";
        String newValue = "NEW_VALUE";
        GlobalConfig config = new GlobalConfig(key, "OLD_VALUE", "Description");
        mockedSecurityUtils.when(SecurityUtils::validateSuperAdminPermission).then(invocation -> null);
        when(globalConfigRepository.findById(key)).thenReturn(Optional.of(config));
        when(globalConfigRepository.save(any(GlobalConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        GlobalConfig updatedConfig = globalConfigService.updateConfig(key, newValue);

        // then
        assertThat(updatedConfig.getConfigValue()).isEqualTo(newValue);
        verify(globalConfigRepository).save(any(GlobalConfig.class));
    }
}

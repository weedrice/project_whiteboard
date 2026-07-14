package com.weedrice.whiteboard.domain.emoticon.service;

import com.weedrice.whiteboard.global.common.service.GlobalConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmoticonImageLimitPolicyTest {

    @Mock
    private GlobalConfigService globalConfigService;

    private EmoticonImageLimitPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new EmoticonImageLimitPolicy(globalConfigService);
    }

    @Test
    @DisplayName("최신 전역 설정의 이미지 제한을 반환한다")
    void getCurrentLimit_returnsFreshConfiguredLimit() {
        when(globalConfigService.getConfigFresh(EmoticonImageLimitPolicy.CONFIG_KEY)).thenReturn(" 35 ");

        int limit = policy.getCurrentLimit();

        assertThat(limit).isEqualTo(35);
        verify(globalConfigService).getConfigFresh(EmoticonImageLimitPolicy.CONFIG_KEY);
    }

    @Test
    @DisplayName("설정이 없거나 잘못되면 기본값 20을 사용한다")
    void getCurrentLimit_invalidValues_useDefault() {
        when(globalConfigService.getConfigFresh(EmoticonImageLimitPolicy.CONFIG_KEY))
                .thenReturn(null, "invalid", "0", "101");

        assertThat(policy.getCurrentLimit()).isEqualTo(EmoticonImageLimitPolicy.DEFAULT_LIMIT);
        assertThat(policy.getCurrentLimit()).isEqualTo(EmoticonImageLimitPolicy.DEFAULT_LIMIT);
        assertThat(policy.getCurrentLimit()).isEqualTo(EmoticonImageLimitPolicy.DEFAULT_LIMIT);
        assertThat(policy.getCurrentLimit()).isEqualTo(EmoticonImageLimitPolicy.DEFAULT_LIMIT);
    }

    @Test
    @DisplayName("요청 개수가 0 이상이고 현재 제한 이하인지 판별한다")
    void isRequestedCountAllowed_checksCurrentBoundary() {
        when(globalConfigService.getConfigFresh(EmoticonImageLimitPolicy.CONFIG_KEY)).thenReturn("3");

        assertThat(policy.isRequestedCountAllowed(-1)).isFalse();
        assertThat(policy.isRequestedCountAllowed(3)).isTrue();
        assertThat(policy.isRequestedCountAllowed(4)).isFalse();
    }
}

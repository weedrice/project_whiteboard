package com.weedrice.whiteboard.domain.emoticon.service;

import com.weedrice.whiteboard.domain.emoticon.repository.EmoticonPurchaseRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmoticonDeletePolicy test")
class EmoticonDeletePolicyTest {

    @Mock
    private EmoticonPurchaseRepository emoticonPurchaseRepository;

    @Test
    @DisplayName("Allows delete when purchase history does not exist")
    void validateDeletable_withoutPurchaseHistory_allowsDelete() {
        EmoticonDeletePolicy deletePolicy = new EmoticonDeletePolicy(emoticonPurchaseRepository);
        when(emoticonPurchaseRepository.existsByEmoticon_EmoticonId(1L)).thenReturn(false);

        assertThatCode(() -> deletePolicy.validateDeletable(1L)).doesNotThrowAnyException();

        verify(emoticonPurchaseRepository).existsByEmoticon_EmoticonId(1L);
    }

    @Test
    @DisplayName("Blocks delete when purchase history exists")
    void validateDeletable_withPurchaseHistory_throwsValidationError() {
        EmoticonDeletePolicy deletePolicy = new EmoticonDeletePolicy(emoticonPurchaseRepository);
        when(emoticonPurchaseRepository.existsByEmoticon_EmoticonId(1L)).thenReturn(true);

        assertThatThrownBy(() -> deletePolicy.validateDeletable(1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(businessException.getMessage()).isEqualTo("구매 이력이 있는 이모티콘은 삭제할 수 없습니다.");
                });
    }
}

package com.weedrice.whiteboard.domain.emoticon.service;

import com.weedrice.whiteboard.domain.emoticon.entity.EmoticonMaster;
import com.weedrice.whiteboard.domain.emoticon.repository.EmoticonMasterRepository;
import com.weedrice.whiteboard.domain.emoticon.repository.EmoticonPurchaseRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmoticonEntitlementGrantService test")
class EmoticonEntitlementGrantServiceTest {

    @Mock
    private EmoticonMasterRepository emoticonMasterRepository;
    @Mock
    private EmoticonPurchaseRepository emoticonPurchaseRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private EntityManager entityManager;

    private EmoticonEntitlementGrantService grantService;
    private User buyer;
    private User creator;
    private EmoticonMaster emoticon;

    @BeforeEach
    void setUp() {
        grantService = new EmoticonEntitlementGrantService(
                emoticonMasterRepository,
                emoticonPurchaseRepository,
                userRepository,
                entityManager);

        buyer = User.builder()
                .loginId("buyer")
                .displayName("buyer")
                .email("buyer@example.com")
                .password("encoded")
                .build();
        ReflectionTestUtils.setField(buyer, "userId", 1L);

        creator = User.builder()
                .loginId("creator")
                .displayName("creator")
                .email("creator@example.com")
                .password("encoded")
                .build();
        ReflectionTestUtils.setField(creator, "userId", 2L);

        emoticon = EmoticonMaster.builder()
                .name("hello")
                .thumbnailUrl("thumb")
                .tags(List.of("tag"))
                .creator(creator)
                .build();
        ReflectionTestUtils.setField(emoticon, "emoticonId", 10L);
    }

    @Nested
    @DisplayName("validate target")
    class ValidateTarget {

        @Test
        @DisplayName("Fails when targetId is missing")
        void validateTargetConfiguration_missingTargetId() {
            assertThatThrownBy(() -> grantService.validateTargetConfiguration(null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("missing-targetId");
        }

        @Test
        @DisplayName("Fails when target does not exist")
        void validateTargetConfiguration_missingTarget() {
            when(emoticonMasterRepository.findById(10L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> grantService.validateTargetConfiguration(10L))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("missing-target");
        }

        @Test
        @DisplayName("Fails when target is inactive")
        void validateTargetConfiguration_inactiveTarget() {
            emoticon.deactivate();
            when(emoticonMasterRepository.findById(10L)).thenReturn(Optional.of(emoticon));

            assertThatThrownBy(() -> grantService.validateTargetConfiguration(10L))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("inactive-target");
        }
    }

    @Nested
    @DisplayName("prepare grant")
    class PrepareGrant {

        @Test
        @DisplayName("Passes for purchasable emoticons")
        void prepareGrant_success() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(buyer));
            when(emoticonMasterRepository.findByIdWithImages(10L)).thenReturn(Optional.of(emoticon));
            when(emoticonPurchaseRepository.existsByUser_UserIdAndEmoticon_EmoticonId(1L, 10L)).thenReturn(false);

            EmoticonEntitlementGrantService.EmoticonGrantContext context = grantService.prepareGrant(1L, 10L);

            assertThat(context.user()).isSameAs(buyer);
            assertThat(context.emoticon()).isSameAs(emoticon);
            verify(emoticonPurchaseRepository).existsByUser_UserIdAndEmoticon_EmoticonId(1L, 10L);
            verify(emoticonPurchaseRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("Fails when already purchased")
        void prepareGrant_duplicatePurchase() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(buyer));
            when(emoticonMasterRepository.findByIdWithImages(10L)).thenReturn(Optional.of(emoticon));
            when(emoticonPurchaseRepository.existsByUser_UserIdAndEmoticon_EmoticonId(1L, 10L)).thenReturn(true);

            assertThatThrownBy(() -> grantService.prepareGrant(1L, 10L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.EMOTICON_ALREADY_PURCHASED);

            verify(emoticonPurchaseRepository, never()).saveAndFlush(any());
        }
    }

    @Nested
    @DisplayName("prepare configured grant")
    class PrepareConfiguredGrant {

        @Test
        @DisplayName("Loads configured emoticon once with images")
        void prepareConfiguredGrant_success() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(buyer));
            when(emoticonMasterRepository.findByIdWithImages(10L)).thenReturn(Optional.of(emoticon));
            when(emoticonPurchaseRepository.existsByUser_UserIdAndEmoticon_EmoticonId(1L, 10L)).thenReturn(false);
            boolean[] priceValidated = {false};

            EmoticonEntitlementGrantService.EmoticonGrantContext context =
                    grantService.prepareConfiguredGrant(1L, 10L, () -> priceValidated[0] = true);

            assertThat(context.user()).isSameAs(buyer);
            assertThat(context.emoticon()).isSameAs(emoticon);
            assertThat(priceValidated[0]).isTrue();
            verify(emoticonMasterRepository).findByIdWithImages(10L);
            verify(emoticonMasterRepository, never()).findById(10L);
        }

        @Test
        @DisplayName("Fails with configuration error when target is inactive")
        void prepareConfiguredGrant_inactiveTarget() {
            emoticon.deactivate();
            when(emoticonMasterRepository.findByIdWithImages(10L)).thenReturn(Optional.of(emoticon));

            assertThatThrownBy(() -> grantService.prepareConfiguredGrant(1L, 10L, () -> {
            }))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("inactive-target");

            verify(userRepository, never()).findById(any());
            verify(emoticonPurchaseRepository, never()).existsByUser_UserIdAndEmoticon_EmoticonId(1L, 10L);
        }

        @Test
        @DisplayName("Runs purchase validation after target configuration and before user duplicate checks")
        void prepareConfiguredGrant_validationFailureStopsBeforeUserChecks() {
            when(emoticonMasterRepository.findByIdWithImages(10L)).thenReturn(Optional.of(emoticon));

            assertThatThrownBy(() -> grantService.prepareConfiguredGrant(1L, 10L, () -> {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
            }))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

            verify(userRepository, never()).findById(any());
            verify(emoticonPurchaseRepository, never()).existsByUser_UserIdAndEmoticon_EmoticonId(1L, 10L);
        }
    }

    @Nested
    @DisplayName("grant")
    class Grant {

        @Test
        @DisplayName("Prepares and grants an emoticon purchase")
        void grant_success() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(buyer));
            when(emoticonMasterRepository.findByIdWithImages(10L)).thenReturn(Optional.of(emoticon));
            when(emoticonPurchaseRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
            when(emoticonMasterRepository.incrementPurchaseCount(10L)).thenReturn(1);
            doAnswer(invocation -> {
                EmoticonMaster refreshed = invocation.getArgument(0);
                ReflectionTestUtils.setField(refreshed, "purchaseCount", 1);
                return null;
            }).when(entityManager).refresh(emoticon);

            EmoticonEntitlementGrantService.EmoticonGrantContext context = grantService.prepareGrant(1L, 10L);
            EmoticonMaster granted = grantService.grant(context, 100);

            assertThat(granted).isSameAs(emoticon);
            assertThat(emoticon.getPurchaseCount()).isEqualTo(1);
            verify(emoticonPurchaseRepository).saveAndFlush(any());
            verify(emoticonMasterRepository).incrementPurchaseCount(10L);
            verify(entityManager).refresh(emoticon);
        }

        @Test
        @DisplayName("Fails when already purchased")
        void grant_duplicatePurchase() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(buyer));
            when(emoticonMasterRepository.findByIdWithImages(10L)).thenReturn(Optional.of(emoticon));
            when(emoticonMasterRepository.incrementPurchaseCount(10L)).thenReturn(1);
            when(emoticonPurchaseRepository.saveAndFlush(any()))
                    .thenThrow(new DataIntegrityViolationException("duplicate"));

            EmoticonEntitlementGrantService.EmoticonGrantContext context = grantService.prepareGrant(1L, 10L);

            assertThatThrownBy(() -> grantService.grant(context, 100))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.EMOTICON_ALREADY_PURCHASED);
            verify(emoticonMasterRepository).incrementPurchaseCount(10L);
            verify(entityManager, never()).refresh(any());
        }

        @Test
        @DisplayName("Fails when purchase count update does not affect a row")
        void grant_purchaseCountUpdateMiss_throwsNotFound() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(buyer));
            when(emoticonMasterRepository.findByIdWithImages(10L)).thenReturn(Optional.of(emoticon));
            when(emoticonMasterRepository.incrementPurchaseCount(10L)).thenReturn(0);

            EmoticonEntitlementGrantService.EmoticonGrantContext context = grantService.prepareGrant(1L, 10L);

            assertThatThrownBy(() -> grantService.grant(context, 100))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.EMOTICON_NOT_FOUND);
            verify(emoticonPurchaseRepository, never()).saveAndFlush(any());
            verify(entityManager, never()).refresh(any());
        }

        @Test
        @DisplayName("Fails when purchasing own emoticon")
        void prepareGrant_cannotPurchaseOwn() {
            when(userRepository.findById(2L)).thenReturn(Optional.of(creator));
            when(emoticonMasterRepository.findByIdWithImages(10L)).thenReturn(Optional.of(emoticon));

            assertThatThrownBy(() -> grantService.prepareGrant(2L, 10L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.EMOTICON_CANNOT_PURCHASE_OWN);
        }
    }
}

package com.weedrice.whiteboard.domain.emoticon.service;

import com.weedrice.whiteboard.domain.emoticon.dto.EmoticonMasterDto;
import com.weedrice.whiteboard.domain.emoticon.entity.EmoticonMaster;
import com.weedrice.whiteboard.domain.emoticon.repository.EmoticonMasterRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.proxy.LazyInitializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmoticonCatalogService 테스트")
class EmoticonCatalogServiceTest {

    @Mock
    private EmoticonMasterRepository emoticonMasterRepository;

    @Mock
    private UserRepository userRepository;

    private EmoticonCatalogService catalogService;

    @BeforeEach
    void setUp() {
        catalogService = new EmoticonCatalogService(
                emoticonMasterRepository,
                userRepository,
                Clock.fixed(Instant.parse("2026-07-07T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void getPopularEmoticons_usesInjectedClockForPeriod() {
        when(emoticonMasterRepository.findPopularEmoticons(LocalDateTime.of(2026, 7, 6, 0, 0), 5))
                .thenReturn(List.of());

        catalogService.getPopularEmoticons("daily");

        verify(emoticonMasterRepository).findPopularEmoticons(LocalDateTime.of(2026, 7, 6, 0, 0), 5);
    }

    @Test
    @DisplayName("작성자 정보가 이미 로딩된 목록은 추가 사용자 조회 없이 매핑한다")
    void getActiveEmoticons_usesLoadedCreatorNameWithoutLookup() {
        User creator = User.builder()
                .loginId("creator")
                .displayName("작성자")
                .email("creator@example.com")
                .password("encoded")
                .build();
        ReflectionTestUtils.setField(creator, "userId", 1L);

        EmoticonMaster master = EmoticonMaster.builder()
                .name("테스트")
                .thumbnailUrl("thumb")
                .tags(List.of("tag"))
                .creator(creator)
                .build();
        ReflectionTestUtils.setField(master, "emoticonId", 10L);

        when(emoticonMasterRepository.findAllActive(PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(master), PageRequest.of(0, 20), 1));

        EmoticonMasterDto result = catalogService.getActiveEmoticons(PageRequest.of(0, 20))
                .getContent()
                .get(0);

        assertThat(result.getCreatorId()).isEqualTo(1L);
        assertThat(result.getCreatorName()).isEqualTo("작성자");
        verify(userRepository, never()).findAllById(any());
    }

    @Test
    @DisplayName("지연 로딩 프록시 작성자는 식별자만 읽고 일괄 사용자 조회로 이름을 채운다")
    void getActiveEmoticons_batchesCreatorLookupForUninitializedProxy() {
        User creatorProxy = mock(User.class, withSettings().extraInterfaces(HibernateProxy.class));
        HibernateProxy proxy = (HibernateProxy) creatorProxy;
        LazyInitializer lazyInitializer = mock(LazyInitializer.class);
        when(proxy.getHibernateLazyInitializer()).thenReturn(lazyInitializer);
        when(lazyInitializer.getIdentifier()).thenReturn(2L);
        when(lazyInitializer.isUninitialized()).thenReturn(true);

        EmoticonMaster master = EmoticonMaster.builder()
                .name("테스트")
                .thumbnailUrl("thumb")
                .tags(List.of("tag"))
                .creator(creatorProxy)
                .build();
        ReflectionTestUtils.setField(master, "emoticonId", 20L);

        User loadedCreator = User.builder()
                .loginId("creator2")
                .displayName("작성자2")
                .email("creator2@example.com")
                .password("encoded")
                .build();
        ReflectionTestUtils.setField(loadedCreator, "userId", 2L);

        when(emoticonMasterRepository.findAllActive(PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(master), PageRequest.of(0, 20), 1));
        when(userRepository.findAllById(any())).thenReturn(List.of(loadedCreator));

        EmoticonMasterDto result = catalogService.getActiveEmoticons(PageRequest.of(0, 20))
                .getContent()
                .get(0);

        assertThat(result.getCreatorId()).isEqualTo(2L);
        assertThat(result.getCreatorName()).isEqualTo("작성자2");
        verify(userRepository).findAllById(any());
    }

    @Test
    @DisplayName("활성 이모티콘 목록은 서비스 경계에서 pageable을 보정한다")
    void getActiveEmoticons_boundsPageableAtServiceBoundary() {
        when(emoticonMasterRepository.findAllActive(any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(List.of(), invocation.getArgument(0), 0));

        catalogService.getActiveEmoticons(PageRequest.of(2, 500, Sort.by("name")));

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(emoticonMasterRepository).findAllActive(pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(100);
        assertThat(pageableCaptor.getValue().getSort().isUnsorted()).isTrue();
    }

    @Test
    @DisplayName("활성 이모티콘 목록은 null pageable을 기본값으로 보정한다")
    void getActiveEmoticons_normalizesNullPageable() {
        when(emoticonMasterRepository.findAllActive(any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(List.of(), invocation.getArgument(0), 0));

        catalogService.getActiveEmoticons(null);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(emoticonMasterRepository).findAllActive(pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(20);
        assertThat(pageableCaptor.getValue().getSort().isUnsorted()).isTrue();
    }
}

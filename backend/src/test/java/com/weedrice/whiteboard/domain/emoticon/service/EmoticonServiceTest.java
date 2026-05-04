package com.weedrice.whiteboard.domain.emoticon.service;

import com.weedrice.whiteboard.domain.emoticon.dto.EmoticonCreateRequest;
import com.weedrice.whiteboard.domain.emoticon.dto.EmoticonMasterDto;
import com.weedrice.whiteboard.domain.emoticon.dto.EmoticonUpdateRequest;
import com.weedrice.whiteboard.domain.emoticon.entity.EmoticonImage;
import com.weedrice.whiteboard.domain.emoticon.entity.EmoticonMaster;
import com.weedrice.whiteboard.domain.emoticon.repository.EmoticonImageRepository;
import com.weedrice.whiteboard.domain.emoticon.repository.EmoticonMasterRepository;
import com.weedrice.whiteboard.domain.emoticon.repository.EmoticonPurchaseRepository;
import com.weedrice.whiteboard.domain.file.service.FileService;
import com.weedrice.whiteboard.domain.point.service.PointService;
import com.weedrice.whiteboard.domain.sanction.service.SanctionService;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.service.UserWritableResolver;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmoticonService 테스트")
class EmoticonServiceTest {

    @Mock
    private EmoticonMasterRepository emoticonMasterRepository;
    @Mock
    private EmoticonImageRepository emoticonImageRepository;
    @Mock
    private EmoticonPurchaseRepository emoticonPurchaseRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PointService pointService;
    @Mock
    private SanctionService sanctionService;
    @Mock
    private UserWritableResolver userWritableResolver;
    @Mock
    private FileService fileService;

    private EmoticonService emoticonService;

    private User user;
    private EmoticonMaster emoticonMaster;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .loginId("testuser")
                .displayName("테스트유저")
                .email("test@example.com")
                .password("encoded")
                .build();
        ReflectionTestUtils.setField(user, "userId", 1L);

        emoticonMaster = EmoticonMaster.builder()
                .name("테스트 이모티콘")
                .thumbnailUrl("https://example.com/thumb.png")
                .tags(List.of("웃음", "감사"))
                .creator(user)
                .build();
        ReflectionTestUtils.setField(emoticonMaster, "emoticonId", 1L);
        lenient().when(userRepository.findAllById(any())).thenReturn(List.of(user));

        EmoticonAttachmentHelper attachmentHelper = new EmoticonAttachmentHelper(fileService);
        EmoticonCatalogService catalogService = new EmoticonCatalogService(emoticonMasterRepository, userRepository, 100);
        EmoticonCommandService commandService = new EmoticonCommandService(
                emoticonMasterRepository,
                emoticonImageRepository,
                emoticonPurchaseRepository,
                userWritableResolver,
                attachmentHelper,
                "EMOTICON_THUMBNAIL",
                "EMOTICON_IMAGE");
        EmoticonEntitlementGrantService grantService = new EmoticonEntitlementGrantService(
                emoticonMasterRepository,
                emoticonPurchaseRepository,
                userRepository);
        EmoticonPurchaseService purchaseService = new EmoticonPurchaseService(
                grantService,
                pointService,
                sanctionService,
                100);
        emoticonService = new EmoticonService(catalogService, commandService, purchaseService);
    }

    private void givenWritableUser() {
        when(userWritableResolver.resolve(1L)).thenReturn(user);
    }

    @Nested
    @DisplayName("목록/상세 조회")
    class GetEmoticons {

        @Test
        @DisplayName("활성 이모티콘 목록 조회 - Pageable 단일 인자 overload")
        void getActiveEmoticons_pageableOnly() {
            Page<EmoticonMaster> page = new PageImpl<>(List.of(emoticonMaster), PageRequest.of(0, 20), 1);
            when(emoticonMasterRepository.findAllActive(any(Pageable.class))).thenReturn(page);

            Page<EmoticonMasterDto> result = emoticonService.getActiveEmoticons(PageRequest.of(0, 20));

            assertThat(result.getContent()).hasSize(1);
            verify(emoticonMasterRepository).findAllActive(any(Pageable.class));
        }

        @Test
        @DisplayName("활성 이모티콘 목록 조회 - latest 정렬")
        void getActiveEmoticons_latest() {
            Page<EmoticonMaster> page = new PageImpl<>(List.of(emoticonMaster), PageRequest.of(0, 20), 1);
            when(emoticonMasterRepository.findAllActiveOrderByCreatedAtAsc(any(Pageable.class))).thenReturn(page);

            Page<EmoticonMasterDto> result = emoticonService.getActiveEmoticons(PageRequest.of(0, 20), "latest");

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getName()).isEqualTo("테스트 이모티콘");
            verify(emoticonMasterRepository).findAllActiveOrderByCreatedAtAsc(any(Pageable.class));
        }

        @Test
        @DisplayName("활성 이모티콘 목록 조회 - popular 정렬")
        void getActiveEmoticons_popular() {
            Page<EmoticonMaster> page = new PageImpl<>(List.of(emoticonMaster), PageRequest.of(0, 20), 1);
            when(emoticonMasterRepository.findAllActiveOrderByPurchaseCount(any(Pageable.class))).thenReturn(page);

            Page<EmoticonMasterDto> result = emoticonService.getActiveEmoticons(PageRequest.of(0, 20), "popular");

            assertThat(result.getContent()).hasSize(1);
            verify(emoticonMasterRepository).findAllActiveOrderByPurchaseCount(any(Pageable.class));
        }

        @Test
        @DisplayName("활성 이모티콘 목록 조회 - POPULAR 대문자")
        void getActiveEmoticons_popularIgnoreCase() {
            Page<EmoticonMaster> page = new PageImpl<>(List.of(emoticonMaster), PageRequest.of(0, 20), 1);
            when(emoticonMasterRepository.findAllActiveOrderByPurchaseCount(any(Pageable.class))).thenReturn(page);

            emoticonService.getActiveEmoticons(PageRequest.of(0, 20), "POPULAR");

            verify(emoticonMasterRepository).findAllActiveOrderByPurchaseCount(any(Pageable.class));
        }

        @Test
        @DisplayName("인기 이모티콘 조회 - daily")
        void getPopularEmoticons_daily() {
            when(emoticonMasterRepository.findPopularEmoticons(any(), eq(5)))
                    .thenReturn(List.of(emoticonMaster));

            List<EmoticonMasterDto> result = emoticonService.getPopularEmoticons("daily");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getName()).isEqualTo("테스트 이모티콘");
            verify(emoticonMasterRepository).findPopularEmoticons(any(), eq(5));
        }

        @Test
        @DisplayName("인기 이모티콘 조회 - weekly, monthly")
        void getPopularEmoticons_weeklyAndMonthly() {
            when(emoticonMasterRepository.findPopularEmoticons(any(), eq(5)))
                    .thenReturn(List.of(emoticonMaster));

            emoticonService.getPopularEmoticons("weekly");
            emoticonService.getPopularEmoticons("monthly");

            verify(emoticonMasterRepository, times(2)).findPopularEmoticons(any(), eq(5));
        }

        @Test
        @DisplayName("인기 이모티콘 조회 - default/unknown period는 daily")
        void getPopularEmoticons_defaultOrUnknown() {
            when(emoticonMasterRepository.findPopularEmoticons(any(), eq(5)))
                    .thenReturn(List.of(emoticonMaster));

            emoticonService.getPopularEmoticons("unknown");
            emoticonService.getPopularEmoticons("");

            verify(emoticonMasterRepository, times(2)).findPopularEmoticons(any(), eq(5));
        }

        @Test
        @DisplayName("이모티콘 상세 조회 성공")
        void getEmoticonDetail_success() {
            when(emoticonMasterRepository.findByIdWithImages(1L)).thenReturn(Optional.of(emoticonMaster));

            EmoticonMasterDto result = emoticonService.getEmoticonDetail(1L, null);

            assertThat(result).isNotNull();
            assertThat(result.getEmoticonId()).isEqualTo(1L);
            assertThat(result.getName()).isEqualTo("테스트 이모티콘");
            verify(emoticonMasterRepository).findByIdWithImages(1L);
        }

        @Test
        @DisplayName("이모티콘 상세 조회 - 존재하지 않으면 EMOTICON_NOT_FOUND")
        void getEmoticonDetail_notFound() {
            when(emoticonMasterRepository.findByIdWithImages(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> emoticonService.getEmoticonDetail(999L, null))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException be = (BusinessException) ex;
                        assertThat(be.getErrorCode()).isEqualTo(ErrorCode.EMOTICON_NOT_FOUND);
                    });
        }

        @Test
        @DisplayName("태그로 검색")
        void searchByTag_success() {
            Page<EmoticonMaster> page = new PageImpl<>(List.of(emoticonMaster), PageRequest.of(0, 20), 1);
            when(emoticonMasterRepository.findByTag(eq("웃음"), any(Pageable.class))).thenReturn(page);

            Page<EmoticonMasterDto> result = emoticonService.searchByTag("웃음", PageRequest.of(0, 20));

            assertThat(result.getContent()).hasSize(1);
            verify(emoticonMasterRepository).findByTag(eq("웃음"), any(Pageable.class));
        }

        @Test
        @DisplayName("키워드로 검색")
        void searchByKeyword_success() {
            Page<EmoticonMaster> page = new PageImpl<>(List.of(emoticonMaster), PageRequest.of(0, 20), 1);
            when(emoticonMasterRepository.findByKeyword(eq("테스트"), any(Pageable.class))).thenReturn(page);

            Page<EmoticonMasterDto> result = emoticonService.searchByKeyword("테스트", PageRequest.of(0, 20));

            assertThat(result.getContent()).hasSize(1);
            verify(emoticonMasterRepository).findByKeyword(eq("테스트"), any(Pageable.class));
        }

        @Test
        @DisplayName("내 이모티콘 목록 조회")
        void getMyEmoticons_success() {
            Page<EmoticonMaster> page = new PageImpl<>(List.of(emoticonMaster), PageRequest.of(0, 20), 1);
            when(emoticonMasterRepository.findByCreatorId(eq(1L), any(Pageable.class))).thenReturn(page);

            Page<EmoticonMasterDto> result = emoticonService.getMyEmoticons(1L, PageRequest.of(0, 20));

            assertThat(result.getContent()).hasSize(1);
            verify(emoticonMasterRepository).findByCreatorId(eq(1L), any(Pageable.class));
        }
    }

    @Nested
    @DisplayName("통합 검색 searchAll")
    class SearchAll {

        @Test
        @DisplayName("키워드 없으면 전체 목록 반환 (latest)")
        void searchAll_emptyKeyword_returnsActiveLatest() {
            Page<EmoticonMaster> page = new PageImpl<>(List.of(emoticonMaster), PageRequest.of(0, 20), 1);
            when(emoticonMasterRepository.findAllActiveOrderByCreatedAtAsc(any(Pageable.class))).thenReturn(page);

            Page<EmoticonMasterDto> result = emoticonService.searchAll(null, "ALL", PageRequest.of(0, 20), "latest");

            assertThat(result.getContent()).hasSize(1);
            verify(emoticonMasterRepository).findAllActiveOrderByCreatedAtAsc(any(Pageable.class));
        }

        @Test
        @DisplayName("searchType NAME으로 검색")
        void searchAll_byName() {
            Page<EmoticonMaster> page = new PageImpl<>(List.of(emoticonMaster), PageRequest.of(0, 20), 1);
            when(emoticonMasterRepository.searchByName(eq("테스트"), any(Pageable.class))).thenReturn(page);

            Page<EmoticonMasterDto> result = emoticonService.searchAll("테스트", "NAME", PageRequest.of(0, 20), "latest");

            assertThat(result.getContent()).hasSize(1);
            verify(emoticonMasterRepository).searchByName(eq("테스트"), any(Pageable.class));
        }

        @Test
        @DisplayName("searchType CREATOR로 검색")
        void searchAll_byCreator() {
            Page<EmoticonMaster> page = new PageImpl<>(List.of(emoticonMaster), PageRequest.of(0, 20), 1);
            when(emoticonMasterRepository.searchByCreator(eq("테스트"), any(Pageable.class))).thenReturn(page);

            Page<EmoticonMasterDto> result = emoticonService.searchAll("테스트", "CREATOR", PageRequest.of(0, 20), "latest");

            assertThat(result.getContent()).hasSize(1);
            verify(emoticonMasterRepository).searchByCreator(eq("테스트"), any(Pageable.class));
        }

        @Test
        @DisplayName("searchType TAG로 검색")
        void searchAll_byTag() {
            Page<EmoticonMaster> page = new PageImpl<>(List.of(emoticonMaster), PageRequest.of(0, 20), 1);
            when(emoticonMasterRepository.findByTag(eq("웃음"), any(Pageable.class))).thenReturn(page);

            Page<EmoticonMasterDto> result = emoticonService.searchAll("웃음", "TAG", PageRequest.of(0, 20), "latest");

            assertThat(result.getContent()).hasSize(1);
            verify(emoticonMasterRepository).findByTag(eq("웃음"), any(Pageable.class));
        }

        @Test
        @DisplayName("searchType ALL로 검색")
        void searchAll_byAll() {
            Page<EmoticonMaster> page = new PageImpl<>(List.of(emoticonMaster), PageRequest.of(0, 20), 1);
            when(emoticonMasterRepository.searchByKeywordAll(eq("키워드"), any(Pageable.class))).thenReturn(page);

            Page<EmoticonMasterDto> result = emoticonService.searchAll("키워드", "ALL", PageRequest.of(0, 20), "latest");

            assertThat(result.getContent()).hasSize(1);
            verify(emoticonMasterRepository).searchByKeywordAll(eq("키워드"), any(Pageable.class));
        }

        @Test
        @DisplayName("키워드 빈 문자열이면 전체 목록 반환")
        void searchAll_emptyStringKeyword() {
            Page<EmoticonMaster> page = new PageImpl<>(List.of(emoticonMaster), PageRequest.of(0, 20), 1);
            when(emoticonMasterRepository.findAllActiveOrderByCreatedAtAsc(any(Pageable.class))).thenReturn(page);

            Page<EmoticonMasterDto> result = emoticonService.searchAll("", "ALL", PageRequest.of(0, 20), "latest");
            Page<EmoticonMasterDto> resultTrimmed = emoticonService.searchAll("   ", "ALL", PageRequest.of(0, 20), "latest");

            assertThat(result.getContent()).hasSize(1);
            assertThat(resultTrimmed.getContent()).hasSize(1);
            verify(emoticonMasterRepository, times(2)).findAllActiveOrderByCreatedAtAsc(any(Pageable.class));
        }

        @Test
        @DisplayName("searchType NAME + popular 정렬")
        void searchAll_byNamePopular() {
            Page<EmoticonMaster> page = new PageImpl<>(List.of(emoticonMaster), PageRequest.of(0, 20), 1);
            when(emoticonMasterRepository.searchByNameOrderByPurchase(eq("테스트"), any(Pageable.class))).thenReturn(page);

            Page<EmoticonMasterDto> result = emoticonService.searchAll("테스트", "NAME", PageRequest.of(0, 20), "popular");

            assertThat(result.getContent()).hasSize(1);
            verify(emoticonMasterRepository).searchByNameOrderByPurchase(eq("테스트"), any(Pageable.class));
        }

        @Test
        @DisplayName("searchType CREATOR + popular 정렬")
        void searchAll_byCreatorPopular() {
            Page<EmoticonMaster> page = new PageImpl<>(List.of(emoticonMaster), PageRequest.of(0, 20), 1);
            when(emoticonMasterRepository.searchByCreatorOrderByPurchase(eq("테스트"), any(Pageable.class))).thenReturn(page);

            Page<EmoticonMasterDto> result = emoticonService.searchAll("테스트", "CREATOR", PageRequest.of(0, 20), "popular");

            assertThat(result.getContent()).hasSize(1);
            verify(emoticonMasterRepository).searchByCreatorOrderByPurchase(eq("테스트"), any(Pageable.class));
        }

        @Test
        @DisplayName("searchType TAG + popular 정렬")
        void searchAll_byTagPopular() {
            Page<EmoticonMaster> page = new PageImpl<>(List.of(emoticonMaster), PageRequest.of(0, 20), 1);
            when(emoticonMasterRepository.searchByTagOrderByPurchase(eq("웃음"), any(Pageable.class))).thenReturn(page);

            Page<EmoticonMasterDto> result = emoticonService.searchAll("웃음", "TAG", PageRequest.of(0, 20), "popular");

            assertThat(result.getContent()).hasSize(1);
            verify(emoticonMasterRepository).searchByTagOrderByPurchase(eq("웃음"), any(Pageable.class));
        }

        @Test
        @DisplayName("searchType ALL + popular 정렬")
        void searchAll_byAllPopular() {
            Page<EmoticonMaster> page = new PageImpl<>(List.of(emoticonMaster), PageRequest.of(0, 20), 1);
            when(emoticonMasterRepository.searchByKeywordAllOrderByPurchase(eq("키워드"), any(Pageable.class))).thenReturn(page);

            Page<EmoticonMasterDto> result = emoticonService.searchAll("키워드", "ALL", PageRequest.of(0, 20), "popular");

            assertThat(result.getContent()).hasSize(1);
            verify(emoticonMasterRepository).searchByKeywordAllOrderByPurchase(eq("키워드"), any(Pageable.class));
        }

        @Test
        @DisplayName("searchType default(알 수 없는 값) -> ALL 검색")
        void searchAll_unknownSearchType() {
            Page<EmoticonMaster> page = new PageImpl<>(List.of(emoticonMaster), PageRequest.of(0, 20), 1);
            when(emoticonMasterRepository.searchByKeywordAll(eq("q"), any(Pageable.class))).thenReturn(page);

            Page<EmoticonMasterDto> result = emoticonService.searchAll("q", "OTHER", PageRequest.of(0, 20), "latest");

            assertThat(result.getContent()).hasSize(1);
            verify(emoticonMasterRepository).searchByKeywordAll(eq("q"), any(Pageable.class));
        }
    }

    @Nested
    @DisplayName("이모티콘 생성")
    class CreateEmoticon {

        @Test
        @DisplayName("이모티콘 생성 성공 - 이미지 없음")
        void createEmoticon_successWithoutImages() {
            EmoticonCreateRequest request = EmoticonCreateRequest.builder()
                    .name("새 이모티콘")
                    .thumbnailFileId(10L)
                    .tags(List.of("태그1"))
                    .build();

            givenWritableUser();
            when(emoticonMasterRepository.save(any(EmoticonMaster.class))).thenAnswer(inv -> {
                EmoticonMaster m = inv.getArgument(0);
                ReflectionTestUtils.setField(m, "emoticonId", 10L);
                return m;
            });

            EmoticonMasterDto result = emoticonService.createEmoticon(1L, request);

            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("새 이모티콘");
            verify(userWritableResolver).resolve(1L);
            verify(emoticonMasterRepository).save(any(EmoticonMaster.class));
        }

        @Test
        @DisplayName("이모티콘 생성 성공 - 이미지 URL 목록 포함")
        void createEmoticon_successWithImages() {
            EmoticonCreateRequest request = EmoticonCreateRequest.builder()
                    .name("새 이모티콘")
                    .thumbnailFileId(10L)
                    .tags(List.of("태그1"))
                    .imageFileIds(List.of(11L, 12L))
                    .build();

            givenWritableUser();
            when(emoticonMasterRepository.save(any(EmoticonMaster.class))).thenAnswer(inv -> {
                EmoticonMaster m = inv.getArgument(0);
                ReflectionTestUtils.setField(m, "emoticonId", 10L);
                return m;
            });

            EmoticonMasterDto result = emoticonService.createEmoticon(1L, request);

            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("새 이모티콘");
            verify(emoticonMasterRepository).save(any(EmoticonMaster.class));
        }

        @Test
        @DisplayName("이모티콘 생성 성공 - imageUrls 빈 리스트면 이미지 추가 안 함")
        void createEmoticon_successWithEmptyImageUrls() {
            EmoticonCreateRequest request = EmoticonCreateRequest.builder()
                    .name("새 이모티콘")
                    .thumbnailFileId(10L)
                    .tags(List.of("태그1"))
                    .imageFileIds(List.of())
                    .build();

            givenWritableUser();
            when(emoticonMasterRepository.save(any(EmoticonMaster.class))).thenAnswer(inv -> {
                EmoticonMaster m = inv.getArgument(0);
                ReflectionTestUtils.setField(m, "emoticonId", 10L);
                return m;
            });

            EmoticonMasterDto result = emoticonService.createEmoticon(1L, request);

            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("새 이모티콘");
            verify(emoticonMasterRepository).save(any(EmoticonMaster.class));
        }

        @Test
        @DisplayName("이모티콘 생성 - 사용자 없으면 USER_NOT_FOUND")
        void createEmoticon_userNotFound() {
            EmoticonCreateRequest request = EmoticonCreateRequest.builder().name("이모티콘").build();
            when(userWritableResolver.resolve(999L))
                    .thenThrow(new BusinessException(ErrorCode.USER_NOT_FOUND));

            assertThatThrownBy(() -> emoticonService.createEmoticon(999L, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND));
        }

        @Test
        @DisplayName("이모티콘 생성 - 제재 사용자는 USER_NOT_ACTIVE")
        void createEmoticon_bannedUser() {
            EmoticonCreateRequest request = EmoticonCreateRequest.builder().name("이모티콘").build();
            when(userWritableResolver.resolve(1L))
                    .thenThrow(new BusinessException(ErrorCode.USER_NOT_ACTIVE));

            assertThatThrownBy(() -> emoticonService.createEmoticon(1L, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.USER_NOT_ACTIVE));

            verify(emoticonMasterRepository, never()).save(any(EmoticonMaster.class));
        }
    }

    @Nested
    @DisplayName("이모티콘 수정/삭제")
    class UpdateAndDelete {

        @Test
        @DisplayName("이모티콘 수정 성공 - 소유자")
        void updateEmoticon_success() {
            EmoticonUpdateRequest request = EmoticonUpdateRequest.builder()
                    .name("수정된 이름")
                    .thumbnailFileId(20L)
                    .tags(List.of("새태그"))
                    .build();

            givenWritableUser();
            when(emoticonMasterRepository.findById(1L)).thenReturn(Optional.of(emoticonMaster));

            EmoticonMasterDto result = emoticonService.updateEmoticon(1L, 1L, request);

            assertThat(result).isNotNull();
            assertThat(emoticonMaster.getName()).isEqualTo("수정된 이름");
            assertThat(emoticonMaster.getThumbnailUrl()).isEqualTo("/api/v1/files/20");
            verify(emoticonMasterRepository).findById(1L);
        }

        @Test
        @DisplayName("이모티콘 수정 - 썸네일 교체 시 기존 썸네일 파일을 삭제 예정으로 전환한다")
        void updateEmoticon_replacingThumbnailDeletesPreviousFile() {
            ReflectionTestUtils.setField(emoticonMaster, "thumbnailUrl", "/api/v1/files/10");
            EmoticonUpdateRequest request = EmoticonUpdateRequest.builder()
                    .thumbnailFileId(20L)
                    .build();

            givenWritableUser();
            when(emoticonMasterRepository.findById(1L)).thenReturn(Optional.of(emoticonMaster));

            emoticonService.updateEmoticon(1L, 1L, request);

            verify(fileService).deleteFileWithStorageIfAssociated(10L, 1L, "EMOTICON_THUMBNAIL");
            assertThat(emoticonMaster.getThumbnailUrl()).isEqualTo("/api/v1/files/20");
        }

        @Test
        @DisplayName("이모티콘 수정 - 같은 썸네일 파일이면 기존 파일을 삭제하지 않는다")
        void updateEmoticon_sameThumbnailFileDoesNotDeletePreviousFile() {
            ReflectionTestUtils.setField(emoticonMaster, "thumbnailUrl", "/api/v1/files/20");
            EmoticonUpdateRequest request = EmoticonUpdateRequest.builder()
                    .thumbnailFileId(20L)
                    .build();

            givenWritableUser();
            when(emoticonMasterRepository.findById(1L)).thenReturn(Optional.of(emoticonMaster));

            emoticonService.updateEmoticon(1L, 1L, request);

            verify(fileService, never()).deleteFileWithStorageIfAssociated(
                    anyLong(),
                    anyLong(),
                    eq("EMOTICON_THUMBNAIL"));
            assertThat(emoticonMaster.getThumbnailUrl()).isEqualTo("/api/v1/files/20");
        }

        @Test
        @DisplayName("이모티콘 수정 - 소유자가 아니면 FORBIDDEN")
        void updateEmoticon_forbidden() {
            when(emoticonMasterRepository.findById(1L)).thenReturn(Optional.of(emoticonMaster));
            EmoticonUpdateRequest request = EmoticonUpdateRequest.builder().name("수정").build();

            assertThatThrownBy(() -> emoticonService.updateEmoticon(2L, 1L, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(EmoticonServiceTest::assertDefaultForbiddenException);

            assertThat(emoticonMaster.getName()).isEqualTo("테스트 이모티콘");
            verify(userWritableResolver, never()).resolve(anyLong());
        }

        @Test
        @DisplayName("이모티콘 수정 - 제재 소유자는 USER_NOT_ACTIVE")
        void updateEmoticon_bannedOwner() {
            when(emoticonMasterRepository.findById(1L)).thenReturn(Optional.of(emoticonMaster));
            when(userWritableResolver.resolve(1L))
                    .thenThrow(new BusinessException(ErrorCode.USER_NOT_ACTIVE));
            EmoticonUpdateRequest request = EmoticonUpdateRequest.builder().name("수정").build();

            assertThatThrownBy(() -> emoticonService.updateEmoticon(1L, 1L, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.USER_NOT_ACTIVE));

            assertThat(emoticonMaster.getName()).isEqualTo("테스트 이모티콘");
        }

        @Test
        @DisplayName("이모티콘 수정 - 일부 필드 null이면 기존 값 유지")
        void updateEmoticon_partialNullKeepsExisting() {
            when(emoticonMasterRepository.findById(1L)).thenReturn(Optional.of(emoticonMaster));
            EmoticonUpdateRequest request = EmoticonUpdateRequest.builder()
                    .name(null)
                    .thumbnailFileId(null)
                    .tags(null)
                    .build();

            givenWritableUser();
            EmoticonMasterDto result = emoticonService.updateEmoticon(1L, 1L, request);

            assertThat(result).isNotNull();
            assertThat(emoticonMaster.getName()).isEqualTo("테스트 이모티콘");
            assertThat(emoticonMaster.getThumbnailUrl()).isEqualTo("https://example.com/thumb.png");
            assertThat(emoticonMaster.getTags()).containsExactly("웃음", "감사");
        }

        @Test
        @DisplayName("이모티콘 공개 전환 - 제재 소유자는 USER_NOT_ACTIVE")
        void toggleVisibility_bannedOwner() {
            when(emoticonMasterRepository.findByIdWithImages(1L)).thenReturn(Optional.of(emoticonMaster));
            when(userWritableResolver.resolve(1L))
                    .thenThrow(new BusinessException(ErrorCode.USER_NOT_ACTIVE));

            assertThatThrownBy(() -> emoticonService.toggleVisibility(1L, 1L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.USER_NOT_ACTIVE));

            assertThat(emoticonMaster.getIsActive()).isEqualTo("Y");
        }

        @Test
        @DisplayName("이모티콘 공개 전환 - 소유자가 아니면 FORBIDDEN")
        void toggleVisibility_forbidden() {
            when(emoticonMasterRepository.findByIdWithImages(1L)).thenReturn(Optional.of(emoticonMaster));

            assertThatThrownBy(() -> emoticonService.toggleVisibility(2L, 1L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(EmoticonServiceTest::assertDefaultForbiddenException);

            assertThat(emoticonMaster.getIsActive()).isEqualTo("Y");
            verify(userWritableResolver, never()).resolve(anyLong());
        }

        @Test
        @DisplayName("이모티콘 수정 - EMOTICON_NOT_FOUND")
        void updateEmoticon_notFound() {
            when(emoticonMasterRepository.findById(999L)).thenReturn(Optional.empty());
            EmoticonUpdateRequest request = EmoticonUpdateRequest.builder().name("x").build();

            assertThatThrownBy(() -> emoticonService.updateEmoticon(1L, 999L, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.EMOTICON_NOT_FOUND));
        }

        @Test
        @DisplayName("이모티콘 삭제 성공")
        void deleteEmoticon_success() {
            givenWritableUser();
            when(emoticonMasterRepository.findByIdWithImages(1L)).thenReturn(Optional.of(emoticonMaster));

            emoticonService.deleteEmoticon(1L, 1L);

            verify(emoticonMasterRepository).delete(emoticonMaster);
        }

        @Test
        @DisplayName("이모티콘 삭제 - 구매 이력이 있으면 hard delete를 차단한다")
        void deleteEmoticon_withPurchaseHistory_blocksHardDelete() {
            ReflectionTestUtils.setField(emoticonMaster, "thumbnailUrl", "/api/v1/files/10");
            givenWritableUser();
            when(emoticonMasterRepository.findByIdWithImages(1L)).thenReturn(Optional.of(emoticonMaster));
            when(emoticonPurchaseRepository.existsByEmoticon_EmoticonId(1L)).thenReturn(true);

            assertThatThrownBy(() -> emoticonService.deleteEmoticon(1L, 1L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.VALIDATION_ERROR));

            verify(fileService, never()).deleteFileWithStorageIfAssociated(anyLong(), anyLong(), anyString());
            verify(emoticonMasterRepository, never()).delete(any(EmoticonMaster.class));
        }

        @Test
        @DisplayName("이모티콘 삭제 - 삭제 중 구매 이력 FK 충돌이 발생하면 비즈니스 예외로 전환한다")
        void deleteEmoticon_purchaseRaceConstraintViolation_throwsBusinessException() {
            givenWritableUser();
            when(emoticonMasterRepository.findByIdWithImages(1L)).thenReturn(Optional.of(emoticonMaster));
            doThrow(new DataIntegrityViolationException("fk")).when(emoticonMasterRepository).flush();

            assertThatThrownBy(() -> emoticonService.deleteEmoticon(1L, 1L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.VALIDATION_ERROR));

            verify(emoticonMasterRepository).delete(emoticonMaster);
            verify(emoticonMasterRepository).flush();
        }

        @Test
        @DisplayName("이모티콘 삭제 - 제재 소유자는 USER_NOT_ACTIVE")
        void deleteEmoticon_bannedOwner() {
            when(emoticonMasterRepository.findByIdWithImages(1L)).thenReturn(Optional.of(emoticonMaster));
            when(userWritableResolver.resolve(1L))
                    .thenThrow(new BusinessException(ErrorCode.USER_NOT_ACTIVE));

            assertThatThrownBy(() -> emoticonService.deleteEmoticon(1L, 1L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.USER_NOT_ACTIVE));

            verify(emoticonMasterRepository, never()).delete(any(EmoticonMaster.class));
            verify(fileService, never()).deleteFileWithStorageIfAssociated(anyLong(), anyLong(), anyString());
        }

        @Test
        @DisplayName("이모티콘 삭제 - 소유자가 아니면 FORBIDDEN")
        void deleteEmoticon_forbidden() {
            when(emoticonMasterRepository.findByIdWithImages(1L)).thenReturn(Optional.of(emoticonMaster));

            assertThatThrownBy(() -> emoticonService.deleteEmoticon(2L, 1L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(EmoticonServiceTest::assertDefaultForbiddenException);

            verify(userWritableResolver, never()).resolve(anyLong());
            verify(emoticonMasterRepository, never()).delete(any(EmoticonMaster.class));
        }

        @Test
        @DisplayName("이모티콘 삭제 - 존재하지 않으면 EMOTICON_NOT_FOUND")
        void deleteEmoticon_notFound() {
            when(emoticonMasterRepository.findByIdWithImages(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> emoticonService.deleteEmoticon(1L, 999L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.EMOTICON_NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("이미지 추가/삭제")
    class ImageOps {

        @Test
        @DisplayName("이미지 추가 성공")
        void addImage_success() {
            givenWritableUser();
            when(emoticonMasterRepository.findByIdWithImages(1L)).thenReturn(Optional.of(emoticonMaster));

            EmoticonMasterDto result = emoticonService.addImage(1L, 1L, 30L);

            assertThat(result).isNotNull();
            assertThat(result.getImages()).hasSize(1);
            assertThat(result.getImages().get(0).getSortOrder()).isEqualTo(0);
            verify(emoticonMasterRepository).findByIdWithImages(1L);
        }

        @Test
        @DisplayName("이미지 삭제 후 재추가는 기존 최대 순번 다음 값을 사용한다")
        void addImage_afterDeletion_usesMaxSortOrderPlusOne() {
            EmoticonImage firstImage = EmoticonImage.builder()
                    .emoticonMaster(emoticonMaster)
                    .imageUrl("/api/v1/files/10")
                    .sortOrder(0)
                    .build();
            ReflectionTestUtils.setField(firstImage, "imageId", 10L);
            EmoticonImage lastImage = EmoticonImage.builder()
                    .emoticonMaster(emoticonMaster)
                    .imageUrl("/api/v1/files/20")
                    .sortOrder(2)
                    .build();
            ReflectionTestUtils.setField(lastImage, "imageId", 20L);
            ReflectionTestUtils.setField(emoticonMaster, "images", new java.util.ArrayList<>(List.of(firstImage, lastImage)));
            givenWritableUser();
            when(emoticonMasterRepository.findByIdWithImages(1L)).thenReturn(Optional.of(emoticonMaster));

            EmoticonMasterDto result = emoticonService.addImage(1L, 1L, 30L);

            assertThat(result.getImages()).extracting("sortOrder").containsExactly(0, 2, 3);
        }

        @Test
        @DisplayName("이미지 추가 - 이모티콘 없으면 EMOTICON_NOT_FOUND")
        void addImage_emoticonNotFound() {
            when(emoticonMasterRepository.findByIdWithImages(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> emoticonService.addImage(1L, 999L, 30L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.EMOTICON_NOT_FOUND));
        }

        @Test
        @DisplayName("이미지 추가 - 소유자가 아니면 FORBIDDEN")
        void addImage_forbidden() {
            when(emoticonMasterRepository.findByIdWithImages(1L)).thenReturn(Optional.of(emoticonMaster));

            assertThatThrownBy(() -> emoticonService.addImage(2L, 1L, 30L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(EmoticonServiceTest::assertDefaultForbiddenException);

            assertThat(emoticonMaster.getImages()).isEmpty();
            verify(userWritableResolver, never()).resolve(anyLong());
        }

        @Test
        @DisplayName("이미지 추가 - 제재 소유자는 USER_NOT_ACTIVE")
        void addImage_bannedOwner() {
            when(emoticonMasterRepository.findByIdWithImages(1L)).thenReturn(Optional.of(emoticonMaster));
            when(userWritableResolver.resolve(1L))
                    .thenThrow(new BusinessException(ErrorCode.USER_NOT_ACTIVE));

            assertThatThrownBy(() -> emoticonService.addImage(1L, 1L, 30L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.USER_NOT_ACTIVE));

            assertThat(emoticonMaster.getImages()).isEmpty();
        }

        @Test
        @DisplayName("이미지 삭제 성공")
        void deleteImage_success() {
            EmoticonImage image = EmoticonImage.builder()
                    .emoticonMaster(emoticonMaster)
                    .imageUrl("https://example.com/img.png")
                    .sortOrder(0)
                    .build();
            ReflectionTestUtils.setField(image, "imageId", 10L);

            givenWritableUser();
            when(emoticonImageRepository.findById(10L)).thenReturn(Optional.of(image));

            emoticonService.deleteImage(1L, 10L);

            verify(emoticonImageRepository).delete(image);
        }

        @Test
        @DisplayName("이미지 삭제 - 이미지 없으면 EMOTICON_IMAGE_NOT_FOUND")
        void deleteImage_notFound() {
            when(emoticonImageRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> emoticonService.deleteImage(1L, 999L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.EMOTICON_IMAGE_NOT_FOUND));
        }

        @Test
        @DisplayName("이미지 삭제 - 소유자가 아니면 FORBIDDEN")
        void deleteImage_forbidden() {
            User otherUser = User.builder().loginId("other").displayName("Other").email("o@o.com").password("p").build();
            ReflectionTestUtils.setField(otherUser, "userId", 99L);
            EmoticonMaster otherMaster = EmoticonMaster.builder()
                    .name("other")
                    .thumbnailUrl("u")
                    .tags(List.of())
                    .creator(otherUser)
                    .build();
            ReflectionTestUtils.setField(otherMaster, "emoticonId", 2L);
            EmoticonImage image = EmoticonImage.builder()
                    .emoticonMaster(otherMaster)
                    .imageUrl("url")
                    .sortOrder(0)
                    .build();
            ReflectionTestUtils.setField(image, "imageId", 10L);

            when(emoticonImageRepository.findById(10L)).thenReturn(Optional.of(image));

            assertThatThrownBy(() -> emoticonService.deleteImage(1L, 10L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(EmoticonServiceTest::assertDefaultForbiddenException);

            verify(userWritableResolver, never()).resolve(anyLong());
            verify(emoticonImageRepository, never()).delete(any());
        }

        @Test
        @DisplayName("이미지 삭제 - 제재 소유자는 USER_NOT_ACTIVE")
        void deleteImage_bannedOwner() {
            EmoticonImage image = EmoticonImage.builder()
                    .emoticonMaster(emoticonMaster)
                    .imageUrl("https://example.com/img.png")
                    .sortOrder(0)
                    .build();
            ReflectionTestUtils.setField(image, "imageId", 10L);

            when(emoticonImageRepository.findById(10L)).thenReturn(Optional.of(image));
            when(userWritableResolver.resolve(1L))
                    .thenThrow(new BusinessException(ErrorCode.USER_NOT_ACTIVE));

            assertThatThrownBy(() -> emoticonService.deleteImage(1L, 10L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.USER_NOT_ACTIVE));

            verify(fileService, never()).deleteFileWithStorageIfAssociated(anyLong(), anyLong(), anyString());
            verify(emoticonImageRepository, never()).delete(any());
        }
    }

    @Nested
    @DisplayName("이모티콘 구매")
    class Purchase {

        @Test
        @DisplayName("이모티콘 구매 성공")
        void purchaseEmoticon_success() {
            User buyer = User.builder().loginId("buyer").displayName("구매자").email("b@ex.com").password("p").build();
            ReflectionTestUtils.setField(buyer, "userId", 2L);
            when(userRepository.findById(2L)).thenReturn(Optional.of(buyer));
            when(emoticonMasterRepository.findByIdWithImages(1L)).thenReturn(Optional.of(emoticonMaster));
            when(emoticonMasterRepository.incrementPurchaseCount(1L)).thenReturn(1);
            doNothing().when(pointService).spendPoint(eq(2L), eq(100), anyString(), eq(1L), eq("EMOTICON"));
            when(emoticonPurchaseRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

            EmoticonMasterDto result = emoticonService.purchaseEmoticon(2L, 1L);

            assertThat(result).isNotNull();
            verify(sanctionService).validateNotBanned(buyer);
            verify(pointService).spendPoint(eq(2L), eq(100), anyString(), eq(1L), eq("EMOTICON"));
            verify(emoticonPurchaseRepository).saveAndFlush(any());
            verify(emoticonMasterRepository).incrementPurchaseCount(1L);
        }

        @Test
        @DisplayName("이모티콘 구매 - 이미 구매한 경우 EMOTICON_ALREADY_PURCHASED")
        void purchaseEmoticon_alreadyPurchased() {
            User buyer = User.builder().loginId("buyer").displayName("구매자").email("b@ex.com").password("p").build();
            ReflectionTestUtils.setField(buyer, "userId", 2L);
            when(userRepository.findById(2L)).thenReturn(Optional.of(buyer));
            when(emoticonMasterRepository.findByIdWithImages(1L)).thenReturn(Optional.of(emoticonMaster));
            when(emoticonPurchaseRepository.saveAndFlush(any()))
                    .thenThrow(new DataIntegrityViolationException("duplicate purchase"));

            assertThatThrownBy(() -> emoticonService.purchaseEmoticon(2L, 1L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.EMOTICON_ALREADY_PURCHASED));

            verify(pointService).spendPoint(eq(2L), eq(100), anyString(), eq(1L), eq("EMOTICON"));
            verify(emoticonMasterRepository, never()).incrementPurchaseCount(any());
        }

        @Test
        @DisplayName("이모티콘 구매 - 본인 등록 이모티콘은 EMOTICON_CANNOT_PURCHASE_OWN")
        void purchaseEmoticon_cannotPurchaseOwn() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(emoticonMasterRepository.findByIdWithImages(1L)).thenReturn(Optional.of(emoticonMaster));
            // emoticonMaster.creator == user (userId 1L) -> isOwner(1L) true

            assertThatThrownBy(() -> emoticonService.purchaseEmoticon(1L, 1L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.EMOTICON_CANNOT_PURCHASE_OWN));

            verify(pointService, never()).spendPoint(any(), anyInt(), anyString(), any(), anyString());
        }

        @Test
        @DisplayName("이모티콘 구매 - 사용자 없으면 USER_NOT_FOUND")
        void purchaseEmoticon_userNotFound() {
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> emoticonService.purchaseEmoticon(999L, 1L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND));
        }

        @Test
        @DisplayName("이모티콘 구매 - 이모티콘 없으면 EMOTICON_NOT_FOUND")
        void purchaseEmoticon_emoticonNotFound() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(emoticonMasterRepository.findByIdWithImages(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> emoticonService.purchaseEmoticon(1L, 999L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.EMOTICON_NOT_FOUND));
        }

        @Test
        @DisplayName("이모티콘 구매 - BAN 사용자는 구매할 수 없다")
        void purchaseEmoticon_bannedUser() {
            User buyer = User.builder().loginId("buyer").displayName("구매자").email("b@ex.com").password("p").build();
            ReflectionTestUtils.setField(buyer, "userId", 2L);
            when(userRepository.findById(2L)).thenReturn(Optional.of(buyer));
            when(emoticonMasterRepository.findByIdWithImages(1L)).thenReturn(Optional.of(emoticonMaster));
            doThrow(new BusinessException(ErrorCode.USER_NOT_ACTIVE)).when(sanctionService).validateNotBanned(buyer);

            assertThatThrownBy(() -> emoticonService.purchaseEmoticon(2L, 1L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.USER_NOT_ACTIVE);

            verify(pointService, never()).spendPoint(any(), anyInt(), anyString(), any(), anyString());
            verify(emoticonPurchaseRepository, never()).saveAndFlush(any());
        }
    }

    @Nested
    @DisplayName("구매 목록 / 사용 가능 여부")
    class PurchasedAndHasPurchased {

        @Test
        @DisplayName("구매한 이모티콘 목록 조회")
        void getPurchasedEmoticons_success() {
            Page<EmoticonMaster> page = new PageImpl<>(List.of(emoticonMaster), PageRequest.of(0, 20), 1);
            when(emoticonMasterRepository.findUsableEmoticons(eq(1L), any(Pageable.class))).thenReturn(page);

            Page<EmoticonMasterDto> result = emoticonService.getPurchasedEmoticons(1L, PageRequest.of(0, 20));

            assertThat(result.getContent()).hasSize(1);
            verify(emoticonMasterRepository).findUsableEmoticons(eq(1L), any(Pageable.class));
        }

        @Test
        @DisplayName("구매 여부 확인 - true")
        void hasPurchased_true() {
            when(emoticonMasterRepository.canUseEmoticon(1L, 1L)).thenReturn(true);

            boolean result = emoticonService.hasPurchased(1L, 1L);

            assertThat(result).isTrue();
            verify(emoticonMasterRepository).canUseEmoticon(1L, 1L);
        }

        @Test
        @DisplayName("구매 여부 확인 - false")
        void hasPurchased_false() {
            when(emoticonMasterRepository.canUseEmoticon(1L, 1L)).thenReturn(false);

            boolean result = emoticonService.hasPurchased(1L, 1L);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("이모티콘 가격 조회")
        void getEmoticonPrice() {
            int price = emoticonService.getEmoticonPrice();

            assertThat(price).isEqualTo(100);
        }
    }

    private static void assertDefaultForbiddenException(Throwable ex) {
        BusinessException businessException = (BusinessException) ex;
        assertThat(businessException.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
        assertThat(businessException.getMessage()).isEqualTo(ErrorCode.FORBIDDEN.getMessage());
    }
}

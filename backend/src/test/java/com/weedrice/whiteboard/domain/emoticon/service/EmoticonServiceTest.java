package com.weedrice.whiteboard.domain.emoticon.service;

import com.weedrice.whiteboard.domain.emoticon.dto.EmoticonCreateRequest;
import com.weedrice.whiteboard.domain.emoticon.dto.EmoticonMasterDto;
import com.weedrice.whiteboard.domain.emoticon.dto.EmoticonPurchaseStatusResponse;
import com.weedrice.whiteboard.domain.emoticon.dto.EmoticonUpdateRequest;
import com.weedrice.whiteboard.domain.emoticon.entity.EmoticonImage;
import com.weedrice.whiteboard.domain.emoticon.entity.EmoticonMaster;
import com.weedrice.whiteboard.domain.emoticon.repository.EmoticonImageRepository;
import com.weedrice.whiteboard.domain.emoticon.repository.EmoticonMasterRepository;
import com.weedrice.whiteboard.domain.emoticon.repository.EmoticonPurchaseRepository;
import com.weedrice.whiteboard.domain.emoticon.repository.EmoticonSearchCondition;
import com.weedrice.whiteboard.domain.emoticon.repository.EmoticonSearchCondition.SearchType;
import com.weedrice.whiteboard.domain.emoticon.repository.EmoticonSearchCondition.SortType;
import com.weedrice.whiteboard.domain.file.service.FileService;
import com.weedrice.whiteboard.domain.shop.entity.ShopItem;
import com.weedrice.whiteboard.domain.shop.repository.ShopItemRepository;
import com.weedrice.whiteboard.domain.shop.service.ShopService;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.service.UserWritableResolver;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import com.weedrice.whiteboard.global.common.service.GlobalConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
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
    private ShopService shopService;
    @Mock
    private ShopItemRepository shopItemRepository;
    @Mock
    private GlobalConfigService globalConfigService;
    @Mock
    private UserWritableResolver userWritableResolver;
    @Mock
    private FileService fileService;
    @Mock
    private EmoticonImageLimitPolicy imageLimitPolicy;

    private EmoticonService emoticonService;

    private User user;
    private EmoticonMaster emoticonMaster;
    private ShopItem emoticonShopItem;

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
        emoticonShopItem = ShopItem.builder()
                .itemName("테스트 이모티콘 상품")
                .price(250)
                .itemType("EMOTICON")
                .targetId(1L)
                .build();
        ReflectionTestUtils.setField(emoticonShopItem, "itemId", 10L);
        lenient().when(userRepository.findAllById(any())).thenReturn(List.of(user));
        lenient().when(imageLimitPolicy.getCurrentLimit()).thenReturn(20);
        lenient().when(shopItemRepository.findByItemTypeAndTargetId("EMOTICON", 1L))
                .thenReturn(List.of(emoticonShopItem));
        lenient().when(shopItemRepository.findByItemTypeAndTargetIdForUpdate("EMOTICON", 1L))
                .thenReturn(List.of(emoticonShopItem));
        lenient().when(globalConfigService.getConfigFresh(GlobalConfigService.NOBICON_PRICE_CONFIG_KEY))
                .thenReturn("100");

        EmoticonAttachmentHelper attachmentHelper = new EmoticonAttachmentHelper(fileService);
        EmoticonCatalogService catalogService = new EmoticonCatalogService(
                emoticonMasterRepository,
                userRepository,
                java.time.Clock.fixed(
                        java.time.Instant.parse("2026-07-07T00:00:00Z"),
                        java.time.ZoneOffset.UTC));
        EmoticonDeletePolicy deletePolicy = new EmoticonDeletePolicy(emoticonPurchaseRepository);
        EmoticonShopItemLifecycleService shopItemLifecycleService = new EmoticonShopItemLifecycleService(
                shopItemRepository,
                globalConfigService);
        EmoticonCommandService commandService = new EmoticonCommandService(
                emoticonMasterRepository,
                emoticonImageRepository,
                userWritableResolver,
                attachmentHelper,
                deletePolicy,
                imageLimitPolicy,
                shopItemLifecycleService,
                "EMOTICON_THUMBNAIL",
                "EMOTICON_IMAGE");
        EmoticonPurchaseService purchaseService = new EmoticonPurchaseService(
                shopService,
                catalogService,
                shopItemLifecycleService);
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
            when(emoticonMasterRepository.findAllActive(any(Pageable.class))).thenReturn(page);

            Page<EmoticonMasterDto> result = emoticonService.getActiveEmoticons(PageRequest.of(0, 20), "latest");

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getName()).isEqualTo("테스트 이모티콘");
            verify(emoticonMasterRepository).findAllActive(any(Pageable.class));
        }

        @Test
        @DisplayName("활성 이모티콘 목록 조회 - oldest 정렬")
        void getActiveEmoticons_oldest() {
            Page<EmoticonMaster> page = new PageImpl<>(List.of(emoticonMaster), PageRequest.of(0, 20), 1);
            when(emoticonMasterRepository.findAllActiveOrderByCreatedAtAsc(any(Pageable.class))).thenReturn(page);

            Page<EmoticonMasterDto> result = emoticonService.getActiveEmoticons(PageRequest.of(0, 20), "oldest");

            assertThat(result.getContent()).hasSize(1);
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
        void getActiveEmoticons_trimsSortBy() {
            Page<EmoticonMaster> page = new PageImpl<>(List.of(emoticonMaster), PageRequest.of(0, 20), 1);
            when(emoticonMasterRepository.findAllActiveOrderByPurchaseCount(any(Pageable.class))).thenReturn(page);

            emoticonService.getActiveEmoticons(PageRequest.of(0, 20), " popular ");

            verify(emoticonMasterRepository).findAllActiveOrderByPurchaseCount(any(Pageable.class));
        }

        @Test
        void getActiveEmoticons_rejectsTooLongSortBy() {
            String longSortBy = "a".repeat(EmoticonRequestNormalizer.MAX_OPTION_LENGTH + 1);

            assertThatThrownBy(() -> emoticonService.getActiveEmoticons(PageRequest.of(0, 20), longSortBy))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);

            verify(emoticonMasterRepository, never()).findAllActive(any(Pageable.class));
            verify(emoticonMasterRepository, never()).findAllActiveOrderByPurchaseCount(any(Pageable.class));
            verify(emoticonMasterRepository, never()).findAllActiveOrderByCreatedAtAsc(any(Pageable.class));
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
        void getPopularEmoticons_defaultsNullAndTrimsPeriod() {
            when(emoticonMasterRepository.findPopularEmoticons(any(), eq(5)))
                    .thenReturn(List.of(emoticonMaster));

            emoticonService.getPopularEmoticons(null);
            emoticonService.getPopularEmoticons(" weekly ");

            verify(emoticonMasterRepository, times(2)).findPopularEmoticons(any(), eq(5));
        }

        @Test
        void getPopularEmoticons_rejectsTooLongPeriod() {
            String longPeriod = "a".repeat(EmoticonRequestNormalizer.MAX_OPTION_LENGTH + 1);

            assertThatThrownBy(() -> emoticonService.getPopularEmoticons(longPeriod))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);

            verify(emoticonMasterRepository, never()).findPopularEmoticons(any(), eq(5));
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
        @DisplayName("search keyword is trimmed and too long keyword is rejected")
        void searchByKeyword_rejectsTooLongKeyword() {
            String longKeyword = " " + "a".repeat(120) + " ";

            assertThatThrownBy(() -> emoticonService.searchByKeyword(longKeyword, PageRequest.of(0, 20)))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);

            verify(emoticonMasterRepository, never()).findByKeyword(anyString(), any(Pageable.class));
        }

        @Test
        @DisplayName("too long tag search is rejected")
        void searchByTag_rejectsTooLongTag() {
            String longTag = "가".repeat(EmoticonRequestNormalizer.MAX_TAG_LENGTH + 1);

            assertThatThrownBy(() -> emoticonService.searchByTag(longTag, PageRequest.of(0, 20)))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);

            verify(emoticonMasterRepository, never()).findByTag(anyString(), any(Pageable.class));
        }

        @Test
        @DisplayName("blank keyword search is rejected")
        void searchByKeyword_blankKeywordRejected() {
            assertThatThrownBy(() -> emoticonService.searchByKeyword("  ", PageRequest.of(0, 20)))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);
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
            when(emoticonMasterRepository.findAllActive(any(Pageable.class))).thenReturn(page);

            Page<EmoticonMasterDto> result = emoticonService.searchAll(null, "ALL", PageRequest.of(0, 20), "latest");

            assertThat(result.getContent()).hasSize(1);
            verify(emoticonMasterRepository).findAllActive(any(Pageable.class));
        }

        @Test
        @DisplayName("searchType NAME으로 검색")
        void searchAll_byName() {
            Page<EmoticonMaster> page = new PageImpl<>(List.of(emoticonMaster), PageRequest.of(0, 20), 1);
            when(emoticonMasterRepository.searchActive(
                    eq(new EmoticonSearchCondition("테스트", SearchType.NAME, SortType.LATEST)),
                    any(Pageable.class))).thenReturn(page);

            Page<EmoticonMasterDto> result = emoticonService.searchAll("테스트", "NAME", PageRequest.of(0, 20), "latest");

            assertThat(result.getContent()).hasSize(1);
            verify(emoticonMasterRepository).searchActive(
                    eq(new EmoticonSearchCondition("테스트", SearchType.NAME, SortType.LATEST)),
                    any(Pageable.class));
        }

        @Test
        @DisplayName("searchAll rejects too long keyword")
        void searchAll_rejectsTooLongKeyword() {
            String longKeyword = " " + "b".repeat(120) + " ";

            assertThatThrownBy(() -> emoticonService.searchAll(longKeyword, "NAME", PageRequest.of(0, 20), "latest"))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);

            verify(emoticonMasterRepository, never())
                    .searchActive(any(EmoticonSearchCondition.class), any(Pageable.class));
        }

        @Test
        @DisplayName("searchAll rejects too long option")
        void searchAll_rejectsTooLongOption() {
            String longSearchType = "a".repeat(EmoticonRequestNormalizer.MAX_OPTION_LENGTH + 1);

            assertThatThrownBy(() -> emoticonService.searchAll(
                    "테스트",
                    longSearchType,
                    PageRequest.of(0, 20),
                    "latest"))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);

            verify(emoticonMasterRepository, never())
                    .searchActive(any(EmoticonSearchCondition.class), any(Pageable.class));
        }

        @Test
        @DisplayName("searchType CREATOR로 검색")
        void searchAll_byCreator() {
            Page<EmoticonMaster> page = new PageImpl<>(List.of(emoticonMaster), PageRequest.of(0, 20), 1);
            when(emoticonMasterRepository.searchActive(
                    eq(new EmoticonSearchCondition("테스트", SearchType.CREATOR, SortType.LATEST)),
                    any(Pageable.class))).thenReturn(page);

            Page<EmoticonMasterDto> result = emoticonService.searchAll("테스트", "CREATOR", PageRequest.of(0, 20), "latest");

            assertThat(result.getContent()).hasSize(1);
            verify(emoticonMasterRepository).searchActive(
                    eq(new EmoticonSearchCondition("테스트", SearchType.CREATOR, SortType.LATEST)),
                    any(Pageable.class));
        }

        @Test
        @DisplayName("searchType TAG로 검색")
        void searchAll_byTag() {
            Page<EmoticonMaster> page = new PageImpl<>(List.of(emoticonMaster), PageRequest.of(0, 20), 1);
            when(emoticonMasterRepository.searchActive(
                    eq(new EmoticonSearchCondition("웃음", SearchType.TAG, SortType.LATEST)),
                    any(Pageable.class))).thenReturn(page);

            Page<EmoticonMasterDto> result = emoticonService.searchAll("웃음", "TAG", PageRequest.of(0, 20), "latest");

            assertThat(result.getContent()).hasSize(1);
            verify(emoticonMasterRepository).searchActive(
                    eq(new EmoticonSearchCondition("웃음", SearchType.TAG, SortType.LATEST)),
                    any(Pageable.class));
        }

        @Test
        @DisplayName("searchType ALL로 검색")
        void searchAll_byAll() {
            Page<EmoticonMaster> page = new PageImpl<>(List.of(emoticonMaster), PageRequest.of(0, 20), 1);
            when(emoticonMasterRepository.searchActive(
                    eq(new EmoticonSearchCondition("키워드", SearchType.ALL, SortType.LATEST)),
                    any(Pageable.class))).thenReturn(page);

            Page<EmoticonMasterDto> result = emoticonService.searchAll("키워드", "ALL", PageRequest.of(0, 20), "latest");

            assertThat(result.getContent()).hasSize(1);
            verify(emoticonMasterRepository).searchActive(
                    eq(new EmoticonSearchCondition("키워드", SearchType.ALL, SortType.LATEST)),
                    any(Pageable.class));
        }

        @Test
        @DisplayName("키워드 빈 문자열이면 전체 목록 반환")
        void searchAll_emptyStringKeyword() {
            Page<EmoticonMaster> page = new PageImpl<>(List.of(emoticonMaster), PageRequest.of(0, 20), 1);
            when(emoticonMasterRepository.findAllActive(any(Pageable.class))).thenReturn(page);

            Page<EmoticonMasterDto> result = emoticonService.searchAll("", "ALL", PageRequest.of(0, 20), "latest");
            Page<EmoticonMasterDto> resultTrimmed = emoticonService.searchAll("   ", "ALL", PageRequest.of(0, 20), "latest");

            assertThat(result.getContent()).hasSize(1);
            assertThat(resultTrimmed.getContent()).hasSize(1);
            verify(emoticonMasterRepository, times(2)).findAllActive(any(Pageable.class));
        }

        @Test
        @DisplayName("searchType NAME + oldest 정렬")
        void searchAll_byNameOldest() {
            Page<EmoticonMaster> page = new PageImpl<>(List.of(emoticonMaster), PageRequest.of(0, 20), 1);
            when(emoticonMasterRepository.searchActive(
                    eq(new EmoticonSearchCondition("테스트", SearchType.NAME, SortType.OLDEST)),
                    any(Pageable.class))).thenReturn(page);

            Page<EmoticonMasterDto> result = emoticonService.searchAll("테스트", "NAME", PageRequest.of(0, 20), "oldest");

            assertThat(result.getContent()).hasSize(1);
            verify(emoticonMasterRepository).searchActive(
                    eq(new EmoticonSearchCondition("테스트", SearchType.NAME, SortType.OLDEST)),
                    any(Pageable.class));
        }

        @Test
        @DisplayName("searchType CREATOR + oldest 정렬")
        void searchAll_byCreatorOldest() {
            Page<EmoticonMaster> page = new PageImpl<>(List.of(emoticonMaster), PageRequest.of(0, 20), 1);
            when(emoticonMasterRepository.searchActive(
                    eq(new EmoticonSearchCondition("테스트", SearchType.CREATOR, SortType.OLDEST)),
                    any(Pageable.class))).thenReturn(page);

            Page<EmoticonMasterDto> result = emoticonService.searchAll("테스트", "CREATOR", PageRequest.of(0, 20), "oldest");

            assertThat(result.getContent()).hasSize(1);
            verify(emoticonMasterRepository).searchActive(
                    eq(new EmoticonSearchCondition("테스트", SearchType.CREATOR, SortType.OLDEST)),
                    any(Pageable.class));
        }

        @Test
        @DisplayName("searchType TAG + oldest 정렬")
        void searchAll_byTagOldest() {
            Page<EmoticonMaster> page = new PageImpl<>(List.of(emoticonMaster), PageRequest.of(0, 20), 1);
            when(emoticonMasterRepository.searchActive(
                    eq(new EmoticonSearchCondition("웃음", SearchType.TAG, SortType.OLDEST)),
                    any(Pageable.class))).thenReturn(page);

            Page<EmoticonMasterDto> result = emoticonService.searchAll("웃음", "TAG", PageRequest.of(0, 20), "oldest");

            assertThat(result.getContent()).hasSize(1);
            verify(emoticonMasterRepository).searchActive(
                    eq(new EmoticonSearchCondition("웃음", SearchType.TAG, SortType.OLDEST)),
                    any(Pageable.class));
        }

        @Test
        @DisplayName("searchType ALL + oldest 정렬")
        void searchAll_byAllOldest() {
            Page<EmoticonMaster> page = new PageImpl<>(List.of(emoticonMaster), PageRequest.of(0, 20), 1);
            when(emoticonMasterRepository.searchActive(
                    eq(new EmoticonSearchCondition("키워드", SearchType.ALL, SortType.OLDEST)),
                    any(Pageable.class))).thenReturn(page);

            Page<EmoticonMasterDto> result = emoticonService.searchAll("키워드", "ALL", PageRequest.of(0, 20), "oldest");

            assertThat(result.getContent()).hasSize(1);
            verify(emoticonMasterRepository).searchActive(
                    eq(new EmoticonSearchCondition("키워드", SearchType.ALL, SortType.OLDEST)),
                    any(Pageable.class));
        }

        @Test
        @DisplayName("searchType NAME + popular 정렬")
        void searchAll_byNamePopular() {
            Page<EmoticonMaster> page = new PageImpl<>(List.of(emoticonMaster), PageRequest.of(0, 20), 1);
            when(emoticonMasterRepository.searchActive(
                    eq(new EmoticonSearchCondition("테스트", SearchType.NAME, SortType.POPULAR)),
                    any(Pageable.class))).thenReturn(page);

            Page<EmoticonMasterDto> result = emoticonService.searchAll("테스트", "NAME", PageRequest.of(0, 20), "popular");

            assertThat(result.getContent()).hasSize(1);
            verify(emoticonMasterRepository).searchActive(
                    eq(new EmoticonSearchCondition("테스트", SearchType.NAME, SortType.POPULAR)),
                    any(Pageable.class));
        }

        @Test
        @DisplayName("searchType CREATOR + popular 정렬")
        void searchAll_byCreatorPopular() {
            Page<EmoticonMaster> page = new PageImpl<>(List.of(emoticonMaster), PageRequest.of(0, 20), 1);
            when(emoticonMasterRepository.searchActive(
                    eq(new EmoticonSearchCondition("테스트", SearchType.CREATOR, SortType.POPULAR)),
                    any(Pageable.class))).thenReturn(page);

            Page<EmoticonMasterDto> result = emoticonService.searchAll("테스트", "CREATOR", PageRequest.of(0, 20), "popular");

            assertThat(result.getContent()).hasSize(1);
            verify(emoticonMasterRepository).searchActive(
                    eq(new EmoticonSearchCondition("테스트", SearchType.CREATOR, SortType.POPULAR)),
                    any(Pageable.class));
        }

        @Test
        @DisplayName("searchType TAG + popular 정렬")
        void searchAll_byTagPopular() {
            Page<EmoticonMaster> page = new PageImpl<>(List.of(emoticonMaster), PageRequest.of(0, 20), 1);
            when(emoticonMasterRepository.searchActive(
                    eq(new EmoticonSearchCondition("웃음", SearchType.TAG, SortType.POPULAR)),
                    any(Pageable.class))).thenReturn(page);

            Page<EmoticonMasterDto> result = emoticonService.searchAll("웃음", "TAG", PageRequest.of(0, 20), "popular");

            assertThat(result.getContent()).hasSize(1);
            verify(emoticonMasterRepository).searchActive(
                    eq(new EmoticonSearchCondition("웃음", SearchType.TAG, SortType.POPULAR)),
                    any(Pageable.class));
        }

        @Test
        @DisplayName("searchType ALL + popular 정렬")
        void searchAll_byAllPopular() {
            Page<EmoticonMaster> page = new PageImpl<>(List.of(emoticonMaster), PageRequest.of(0, 20), 1);
            when(emoticonMasterRepository.searchActive(
                    eq(new EmoticonSearchCondition("키워드", SearchType.ALL, SortType.POPULAR)),
                    any(Pageable.class))).thenReturn(page);

            Page<EmoticonMasterDto> result = emoticonService.searchAll("키워드", "ALL", PageRequest.of(0, 20), "popular");

            assertThat(result.getContent()).hasSize(1);
            verify(emoticonMasterRepository).searchActive(
                    eq(new EmoticonSearchCondition("키워드", SearchType.ALL, SortType.POPULAR)),
                    any(Pageable.class));
        }

        @Test
        @DisplayName("null searchType defaults to ALL")
        void searchAll_nullSearchType_defaultsToAll() {
            Page<EmoticonMaster> page = new PageImpl<>(List.of(emoticonMaster), PageRequest.of(0, 20), 1);
            when(emoticonMasterRepository.searchActive(
                    eq(new EmoticonSearchCondition("q", SearchType.ALL, SortType.LATEST)),
                    any(Pageable.class))).thenReturn(page);

            Page<EmoticonMasterDto> result = emoticonService.searchAll("q", null, PageRequest.of(0, 20), "latest");

            assertThat(result.getContent()).hasSize(1);
            verify(emoticonMasterRepository).searchActive(
                    eq(new EmoticonSearchCondition("q", SearchType.ALL, SortType.LATEST)),
                    any(Pageable.class));
        }

        @Test
        @DisplayName("unknown searchType is rejected")
        void searchAll_unknownSearchType_invalidInput() {
            assertThatThrownBy(() -> emoticonService.searchAll("q", "OTHER", PageRequest.of(0, 20), "latest"))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);
        }

        @Test
        @DisplayName("blank keyword still validates searchType")
        void searchAll_blankKeywordUnknownSearchType_invalidInput() {
            assertThatThrownBy(() -> emoticonService.searchAll(" ", "OTHER", PageRequest.of(0, 20), "latest"))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);
        }

        @Test
        @DisplayName("searchType is trimmed and normalized")
        void searchAll_searchTypeTrimmedAndNormalized() {
            Page<EmoticonMaster> page = new PageImpl<>(List.of(emoticonMaster), PageRequest.of(0, 20), 1);
            when(emoticonMasterRepository.searchActive(
                    eq(new EmoticonSearchCondition("q", SearchType.NAME, SortType.LATEST)),
                    any(Pageable.class))).thenReturn(page);

            Page<EmoticonMasterDto> result = emoticonService.searchAll("q", " name ", PageRequest.of(0, 20), "latest");

            assertThat(result.getContent()).hasSize(1);
            verify(emoticonMasterRepository).searchActive(
                    eq(new EmoticonSearchCondition("q", SearchType.NAME, SortType.LATEST)),
                    any(Pageable.class));
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
        @DisplayName("이모티콘 생성 시 현재 설정 가격을 상품 스냅샷으로 저장한다")
        void createEmoticon_usesCurrentPriceSnapshot() {
            EmoticonCreateRequest request = EmoticonCreateRequest.builder().name("가격 상품").build();
            givenWritableUser();
            when(globalConfigService.getConfigFresh(GlobalConfigService.NOBICON_PRICE_CONFIG_KEY)).thenReturn("275");
            when(emoticonMasterRepository.save(any(EmoticonMaster.class))).thenAnswer(invocation -> {
                EmoticonMaster master = invocation.getArgument(0);
                ReflectionTestUtils.setField(master, "emoticonId", 10L);
                return master;
            });

            emoticonService.createEmoticon(1L, request);

            verify(shopItemRepository).save(argThat(item -> item.getPrice() == 275
                    && item.getTargetId() == 10L
                    && "EMOTICON".equals(item.getItemType())));
        }

        @Test
        @DisplayName("상품 저장 실패는 이모티콘 생성 오류로 전파된다")
        void createEmoticon_shopItemSaveFailurePropagates() {
            EmoticonCreateRequest request = EmoticonCreateRequest.builder().name("실패 상품").build();
            givenWritableUser();
            when(emoticonMasterRepository.save(any(EmoticonMaster.class))).thenAnswer(invocation -> {
                EmoticonMaster master = invocation.getArgument(0);
                ReflectionTestUtils.setField(master, "emoticonId", 10L);
                return master;
            });
            when(shopItemRepository.save(any(ShopItem.class)))
                    .thenThrow(new DataIntegrityViolationException("shop item save failed"));

            assertThatThrownBy(() -> emoticonService.createEmoticon(1L, request))
                    .isInstanceOf(DataIntegrityViolationException.class);
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
            when(fileService.associateFilesWithEntity(
                    eq(List.of(11L, 12L)),
                    eq(1L),
                    eq(10L),
                    eq("EMOTICON_IMAGE"),
                    eq(20)))
                    .thenReturn(List.of("/api/v1/files/11", "/api/v1/files/12"));

            EmoticonMasterDto result = emoticonService.createEmoticon(1L, request);

            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("새 이모티콘");
            assertThat(result.getImages()).extracting("imageUrl")
                    .containsExactly("/api/v1/files/11", "/api/v1/files/12");
            assertThat(result.getImages()).extracting("sortOrder")
                    .containsExactly(0, 1);
            verify(emoticonMasterRepository).save(any(EmoticonMaster.class));
            verify(fileService).associateFilesWithEntity(
                    eq(List.of(11L, 12L)),
                    eq(1L),
                    eq(10L),
                    eq("EMOTICON_IMAGE"),
                    eq(20));
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
        @DisplayName("이모티콘 생성 - imageFileIds 중복은 저장 전 거부한다")
        void createEmoticon_rejectsDuplicateImageFileIdsBeforeSave() {
            EmoticonCreateRequest request = EmoticonCreateRequest.builder()
                    .name("새 이모티콘")
                    .thumbnailFileId(10L)
                    .tags(List.of("태그1"))
                    .imageFileIds(List.of(11L, 12L, 11L))
                    .build();

            assertThatThrownBy(() -> emoticonService.createEmoticon(1L, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.EMOTICON_DUPLICATE_IMAGE_FILE));

            verify(userWritableResolver, never()).resolve(anyLong());
            verify(emoticonMasterRepository, never()).save(any(EmoticonMaster.class));
            verify(fileService, never()).associateFileWithEntity(any(), any(), any(), any());
            verify(fileService, never()).associateFilesWithEntity(any(), any(), any(), any(), anyInt());
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
                    .name(" 수정된 이름 ")
                    .thumbnailFileId(20L)
                    .tags(List.of("새태그"))
                    .build();

            givenWritableUser();
            when(emoticonMasterRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(emoticonMaster));

            EmoticonMasterDto result = emoticonService.updateEmoticon(1L, 1L, request);

            assertThat(result).isNotNull();
            assertThat(emoticonMaster.getName()).isEqualTo("수정된 이름");
            assertThat(emoticonMaster.getThumbnailUrl()).isEqualTo("/api/v1/files/20");
            assertThat(emoticonShopItem.getItemName()).isEqualTo("수정된 이름");
            assertThat(emoticonShopItem.getImageUrl()).isEqualTo("/api/v1/files/20");
            assertThat(emoticonShopItem.getPrice()).isEqualTo(250);
            verify(emoticonMasterRepository).findByIdForUpdate(1L);
        }

        @Test
        @DisplayName("emoticon update rejects blank name")
        void updateEmoticon_blankName_invalidInput() {
            when(emoticonMasterRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(emoticonMaster));
            EmoticonUpdateRequest request = EmoticonUpdateRequest.builder().name("   ").build();
            String previousName = emoticonMaster.getName();

            givenWritableUser();

            assertThatThrownBy(() -> emoticonService.updateEmoticon(1L, 1L, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.VALIDATION_ERROR));

            assertThat(emoticonMaster.getName()).isEqualTo(previousName);
        }

        @Test
        @DisplayName("이모티콘 수정 - 썸네일 교체 시 기존 썸네일 파일을 삭제 예정으로 전환한다")
        void updateEmoticon_replacingThumbnailDeletesPreviousFile() {
            ReflectionTestUtils.setField(emoticonMaster, "thumbnailUrl", "/api/v1/files/10");
            EmoticonUpdateRequest request = EmoticonUpdateRequest.builder()
                    .thumbnailFileId(20L)
                    .build();

            givenWritableUser();
            when(emoticonMasterRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(emoticonMaster));

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
            when(emoticonMasterRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(emoticonMaster));

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
            when(emoticonMasterRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(emoticonMaster));
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
            when(emoticonMasterRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(emoticonMaster));
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
            when(emoticonMasterRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(emoticonMaster));
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
        @DisplayName("이모티콘 수정 - 이미지 삭제와 추가를 한 요청으로 처리하고 기존 최대 순번 다음부터 추가한다")
        void updateEmoticon_deleteAndAddImages_successWithNextSortOrder() {
            ReflectionTestUtils.setField(emoticonMaster, "images", emoticonImages(3));
            EmoticonUpdateRequest request = EmoticonUpdateRequest.builder()
                    .addImageFileIds(List.of(500L, 501L))
                    .deleteImageIds(List.of(101L))
                    .build();

            givenWritableUser();
            when(emoticonMasterRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(emoticonMaster));
            when(fileService.associateFilesWithEntity(
                    eq(List.of(500L, 501L)),
                    eq(1L),
                    eq(1L),
                    eq("EMOTICON_IMAGE"),
                    eq(20)))
                    .thenReturn(List.of("/api/v1/files/500", "/api/v1/files/501"));

            EmoticonMasterDto result = emoticonService.updateEmoticon(1L, 1L, request);

            assertThat(result.getImages()).extracting("imageUrl")
                    .containsExactly(
                            "/api/v1/files/100",
                            "/api/v1/files/102",
                            "/api/v1/files/500",
                            "/api/v1/files/501");
            assertThat(result.getImages()).extracting("sortOrder").containsExactly(0, 2, 3, 4);
            verify(fileService).deleteFileWithStorageIfAssociated(101L, 1L, "EMOTICON_IMAGE");
            verify(fileService).associateFilesWithEntity(
                    List.of(500L, 501L), 1L, 1L, "EMOTICON_IMAGE", 20);
        }

        @Test
        @DisplayName("이모티콘 수정 - 다른 팩의 이미지 ID 삭제는 EMOTICON_IMAGE_NOT_FOUND")
        void updateEmoticon_deleteImageFromOtherPack_rejected() {
            ReflectionTestUtils.setField(emoticonMaster, "images", emoticonImages(2));
            EmoticonUpdateRequest request = EmoticonUpdateRequest.builder()
                    .deleteImageIds(List.of(900L))
                    .build();

            givenWritableUser();
            when(emoticonMasterRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(emoticonMaster));

            assertThatThrownBy(() -> emoticonService.updateEmoticon(1L, 1L, request))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EMOTICON_IMAGE_NOT_FOUND);

            assertThat(emoticonMaster.getImages()).hasSize(2);
            verifyNoInteractions(fileService);
        }

        @Test
        @DisplayName("이미 한도를 초과한 팩은 메타데이터 수정과 이미지 삭제를 허용한다")
        void updateEmoticon_existingOverLimitPack_allowsMetadataAndDeletion() {
            ReflectionTestUtils.setField(emoticonMaster, "images", emoticonImages(21));
            EmoticonUpdateRequest request = EmoticonUpdateRequest.builder()
                    .name("정리한 이모티콘")
                    .deleteImageIds(List.of(100L))
                    .build();

            givenWritableUser();
            when(emoticonMasterRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(emoticonMaster));

            EmoticonMasterDto result = emoticonService.updateEmoticon(1L, 1L, request);

            assertThat(result.getName()).isEqualTo("정리한 이모티콘");
            assertThat(result.getImages()).hasSize(20);
            verify(fileService).deleteFileWithStorageIfAssociated(100L, 1L, "EMOTICON_IMAGE");
        }

        @Test
        @DisplayName("이미 한도를 초과한 팩은 동시 삭제가 있어도 이미지 추가를 거부한다")
        void updateEmoticon_existingOverLimitPack_rejectsAdditionEvenWithDeletion() {
            ReflectionTestUtils.setField(emoticonMaster, "images", emoticonImages(21));
            EmoticonUpdateRequest request = EmoticonUpdateRequest.builder()
                    .addImageFileIds(List.of(500L))
                    .deleteImageIds(List.of(100L, 101L))
                    .build();

            givenWritableUser();
            when(emoticonMasterRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(emoticonMaster));

            assertThatThrownBy(() -> emoticonService.updateEmoticon(1L, 1L, request))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EMOTICON_IMAGE_LIMIT_EXCEEDED);

            assertThat(emoticonMaster.getImages()).hasSize(21);
            verifyNoInteractions(fileService);
        }

        @Test
        @DisplayName("이모티콘 수정 - 이미 연결된 파일 ID 재추가는 중복 오류")
        void updateEmoticon_readdingAlreadyAttachedFile_rejectedAsDuplicate() {
            ReflectionTestUtils.setField(emoticonMaster, "images", emoticonImages(2));
            EmoticonUpdateRequest request = EmoticonUpdateRequest.builder()
                    .addImageFileIds(List.of(101L))
                    .build();

            givenWritableUser();
            when(emoticonMasterRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(emoticonMaster));

            assertThatThrownBy(() -> emoticonService.updateEmoticon(1L, 1L, request))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EMOTICON_DUPLICATE_IMAGE_FILE);

            assertThat(emoticonMaster.getImages()).hasSize(2);
            verifyNoInteractions(fileService);
        }

        @Test
        @DisplayName("이모티콘 공개 전환 - 제재 소유자는 USER_NOT_ACTIVE")
        void toggleVisibility_bannedOwner() {
            when(emoticonMasterRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(emoticonMaster));
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
            when(emoticonMasterRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(emoticonMaster));

            assertThatThrownBy(() -> emoticonService.toggleVisibility(2L, 1L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(EmoticonServiceTest::assertDefaultForbiddenException);

            assertThat(emoticonMaster.getIsActive()).isEqualTo("Y");
            verify(userWritableResolver, never()).resolve(anyLong());
        }

        @Test
        @DisplayName("이모티콘 공개 상태를 상품 활성 상태와 동기화한다")
        void toggleVisibility_synchronizesShopItem() {
            givenWritableUser();
            when(emoticonMasterRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(emoticonMaster));

            emoticonService.toggleVisibility(1L, 1L);

            assertThat(emoticonMaster.getIsActive()).isEqualTo("N");
            assertThat(emoticonShopItem.getIsActive()).isFalse();
            InOrder lockOrder = inOrder(emoticonMasterRepository, shopItemRepository);
            lockOrder.verify(shopItemRepository).findByItemTypeAndTargetIdForUpdate("EMOTICON", 1L);
            lockOrder.verify(emoticonMasterRepository).findByIdForUpdate(1L);
        }

        @Test
        @DisplayName("이모티콘 수정 - EMOTICON_NOT_FOUND")
        void updateEmoticon_notFound() {
            when(emoticonMasterRepository.findByIdForUpdate(999L)).thenReturn(Optional.empty());
            EmoticonUpdateRequest request = EmoticonUpdateRequest.builder().name("x").build();

            assertThatThrownBy(() -> emoticonService.updateEmoticon(1L, 999L, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.EMOTICON_NOT_FOUND));
        }

        @Test
        @DisplayName("이모티콘 삭제 성공")
        void deleteEmoticon_success() {
            ReflectionTestUtils.setField(emoticonMaster, "thumbnailUrl", "/api/v1/files/10");
            EmoticonImage image = EmoticonImage.builder()
                    .emoticonMaster(emoticonMaster)
                    .imageUrl("/api/v1/files/20")
                    .sortOrder(0)
                    .build();
            ReflectionTestUtils.setField(image, "imageId", 20L);
            ReflectionTestUtils.setField(emoticonMaster, "images", new java.util.ArrayList<>(List.of(image)));
            givenWritableUser();
            when(emoticonMasterRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(emoticonMaster));

            emoticonService.deleteEmoticon(1L, 1L);

            InOrder lockOrder = inOrder(shopItemRepository, emoticonMasterRepository);
            lockOrder.verify(shopItemRepository).findByItemTypeAndTargetIdForUpdate("EMOTICON", 1L);
            lockOrder.verify(emoticonMasterRepository).findByIdForUpdate(1L);
            verify(emoticonMasterRepository).findByIdForUpdate(1L);
            InOrder inOrder = inOrder(emoticonMasterRepository, fileService);
            inOrder.verify(emoticonMasterRepository).delete(emoticonMaster);
            inOrder.verify(emoticonMasterRepository).flush();
            inOrder.verify(fileService).deleteFileWithStorageIfAssociated(10L, 1L, "EMOTICON_THUMBNAIL");
            inOrder.verify(fileService).deleteFileWithStorageIfAssociated(20L, 1L, "EMOTICON_IMAGE");
            assertThat(emoticonShopItem.getIsActive()).isFalse();
            assertThat(emoticonShopItem.getTargetId()).isNull();
        }

        @Test
        @DisplayName("이모티콘 삭제 - 구매 이력이 있으면 hard delete를 차단한다")
        void deleteEmoticon_withPurchaseHistory_blocksHardDelete() {
            ReflectionTestUtils.setField(emoticonMaster, "thumbnailUrl", "/api/v1/files/10");
            givenWritableUser();
            when(emoticonMasterRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(emoticonMaster));
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
            ReflectionTestUtils.setField(emoticonMaster, "thumbnailUrl", "/api/v1/files/10");
            givenWritableUser();
            when(emoticonMasterRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(emoticonMaster));
            doThrow(new DataIntegrityViolationException("fk")).when(emoticonMasterRepository).flush();

            assertThatThrownBy(() -> emoticonService.deleteEmoticon(1L, 1L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.VALIDATION_ERROR));

            verify(emoticonMasterRepository).delete(emoticonMaster);
            verify(emoticonMasterRepository).flush();
            verify(fileService, never()).deleteFileWithStorageIfAssociated(anyLong(), anyLong(), anyString());
        }

        @Test
        @DisplayName("이모티콘 삭제 - 제재 소유자는 USER_NOT_ACTIVE")
        void deleteEmoticon_bannedOwner() {
            when(emoticonMasterRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(emoticonMaster));
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
            when(emoticonMasterRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(emoticonMaster));

            assertThatThrownBy(() -> emoticonService.deleteEmoticon(2L, 1L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(EmoticonServiceTest::assertDefaultForbiddenException);

            verify(userWritableResolver, never()).resolve(anyLong());
            verify(emoticonMasterRepository, never()).delete(any(EmoticonMaster.class));
        }

        @Test
        @DisplayName("이모티콘 삭제 - 존재하지 않으면 EMOTICON_NOT_FOUND")
        void deleteEmoticon_notFound() {
            when(emoticonMasterRepository.findByIdForUpdate(999L)).thenReturn(Optional.empty());

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
            when(emoticonMasterRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(emoticonMaster));

            EmoticonMasterDto result = emoticonService.addImage(1L, 1L, 30L);

            assertThat(result).isNotNull();
            assertThat(result.getImages()).hasSize(1);
            assertThat(result.getImages().get(0).getSortOrder()).isEqualTo(0);
            verify(emoticonMasterRepository).findByIdForUpdate(1L);
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
            when(emoticonMasterRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(emoticonMaster));

            EmoticonMasterDto result = emoticonService.addImage(1L, 1L, 30L);

            assertThat(result.getImages()).extracting("sortOrder").containsExactly(0, 2, 3);
        }

        @Test
        @DisplayName("이미지 추가 - 최대 이미지 수를 초과할 수 없다")
        void addImage_rejectsWhenImageCountLimitReached() {
            ReflectionTestUtils.setField(emoticonMaster, "images", emoticonImages(20));
            givenWritableUser();
            when(emoticonMasterRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(emoticonMaster));

            assertThatThrownBy(() -> emoticonService.addImage(1L, 1L, 30L))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EMOTICON_IMAGE_LIMIT_EXCEEDED);

            assertThat(emoticonMaster.getImages()).hasSize(20);
            verify(fileService, never()).associateFileWithEntity(anyLong(), anyLong(), anyLong(), anyString());
        }

        @Test
        @DisplayName("동적 한도가 100이면 100번째는 허용하고 101번째는 거부한다")
        void addImage_dynamicLimit100_allows100AndRejects101() {
            ReflectionTestUtils.setField(emoticonMaster, "images", emoticonImages(99));
            givenWritableUser();
            when(imageLimitPolicy.getCurrentLimit()).thenReturn(100);
            when(emoticonMasterRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(emoticonMaster));

            EmoticonMasterDto result = emoticonService.addImage(1L, 1L, 500L);

            assertThat(result.getImages()).hasSize(100);
            assertThat(result.getImages().get(99).getSortOrder()).isEqualTo(99);

            assertThatThrownBy(() -> emoticonService.addImage(1L, 1L, 501L))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EMOTICON_IMAGE_LIMIT_EXCEEDED);

            assertThat(emoticonMaster.getImages()).hasSize(100);
            verify(fileService).associateFileWithEntity(500L, 1L, 1L, "EMOTICON_IMAGE");
            verify(fileService, never()).associateFileWithEntity(501L, 1L, 1L, "EMOTICON_IMAGE");
        }

        @Test
        @DisplayName("이미지 추가 - 이모티콘 없으면 EMOTICON_NOT_FOUND")
        void addImage_emoticonNotFound() {
            when(emoticonMasterRepository.findByIdForUpdate(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> emoticonService.addImage(1L, 999L, 30L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.EMOTICON_NOT_FOUND));
        }

        @Test
        @DisplayName("이미지 추가 - 소유자가 아니면 FORBIDDEN")
        void addImage_forbidden() {
            when(emoticonMasterRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(emoticonMaster));

            assertThatThrownBy(() -> emoticonService.addImage(2L, 1L, 30L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(EmoticonServiceTest::assertDefaultForbiddenException);

            assertThat(emoticonMaster.getImages()).isEmpty();
            verify(userWritableResolver, never()).resolve(anyLong());
        }

        @Test
        @DisplayName("이미지 추가 - 제재 소유자는 USER_NOT_ACTIVE")
        void addImage_bannedOwner() {
            when(emoticonMasterRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(emoticonMaster));
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
            ReflectionTestUtils.setField(emoticonMaster, "images", new java.util.ArrayList<>(List.of(image)));

            givenWritableUser();
            when(emoticonImageRepository.findById(10L)).thenReturn(Optional.of(image));
            when(emoticonMasterRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(emoticonMaster));

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
            ReflectionTestUtils.setField(otherMaster, "images", new java.util.ArrayList<>(List.of(image)));

            when(emoticonImageRepository.findById(10L)).thenReturn(Optional.of(image));
            when(emoticonMasterRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(otherMaster));

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
            ReflectionTestUtils.setField(emoticonMaster, "images", new java.util.ArrayList<>(List.of(image)));

            when(emoticonImageRepository.findById(10L)).thenReturn(Optional.of(image));
            when(emoticonMasterRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(emoticonMaster));
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
        @DisplayName("이모티콘 구매는 상점 구매 command로 위임하고 상세를 반환한다")
        void purchaseEmoticon_success() {
            when(emoticonMasterRepository.findByIdWithImages(1L)).thenReturn(Optional.of(emoticonMaster));

            EmoticonMasterDto result = emoticonService.purchaseEmoticon(2L, 1L);

            assertThat(result).isNotNull();
            assertThat(result.getEmoticonId()).isEqualTo(1L);
            verify(shopService).purchaseActiveItemByTarget(2L, "EMOTICON", 1L);
            verify(emoticonPurchaseRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("이모티콘 구매 - 상점 구매 실패를 전파한다")
        void purchaseEmoticon_alreadyPurchased() {
            doThrow(new BusinessException(ErrorCode.EMOTICON_ALREADY_PURCHASED))
                    .when(shopService)
                    .purchaseActiveItemByTarget(2L, "EMOTICON", 1L);

            assertThatThrownBy(() -> emoticonService.purchaseEmoticon(2L, 1L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.EMOTICON_ALREADY_PURCHASED));

            verify(emoticonMasterRepository, never()).incrementPurchaseCount(any());
        }

        @Test
        @DisplayName("이모티콘 구매 - active 상점 상품이 없으면 ITEM_NOT_AVAILABLE")
        void purchaseEmoticon_missingShopItem() {
            doThrow(new BusinessException(ErrorCode.ITEM_NOT_AVAILABLE))
                    .when(shopService)
                    .purchaseActiveItemByTarget(2L, "EMOTICON", 1L);

            assertThatThrownBy(() -> emoticonService.purchaseEmoticon(2L, 1L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.ITEM_NOT_AVAILABLE));

            verify(emoticonMasterRepository, never()).findByIdWithImages(anyLong());
        }

        @Test
        @DisplayName("이모티콘 구매 - active 상점 상품이 중복이면 ITEM_NOT_AVAILABLE")
        void purchaseEmoticon_duplicateShopItems() {
            doThrow(new BusinessException(ErrorCode.ITEM_NOT_AVAILABLE))
                    .when(shopService)
                    .purchaseActiveItemByTarget(2L, "EMOTICON", 1L);

            assertThatThrownBy(() -> emoticonService.purchaseEmoticon(2L, 1L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.ITEM_NOT_AVAILABLE));

            verify(emoticonMasterRepository, never()).findByIdWithImages(anyLong());
        }

        @Test
        @DisplayName("이모티콘 가격 조회는 active 상점 상품 가격을 반환한다")
        void getEmoticonPrice_fromShopItem() {
            when(shopService.resolveSingleActiveItemByTarget("EMOTICON", 1L)).thenReturn(emoticonShopItem);

            int price = emoticonService.getEmoticonPrice(1L);

            assertThat(price).isEqualTo(250);
            verify(shopService).resolveSingleActiveItemByTarget("EMOTICON", 1L);
        }
    }

    @Nested
    @DisplayName("구매 목록 / 사용 가능 여부")
    class PurchasedAndHasPurchased {

        @Test
        @DisplayName("구매한 이모티콘 목록 조회")
        void getPurchasedEmoticons_success() {
            Page<EmoticonMaster> page = new PageImpl<>(List.of(emoticonMaster), PageRequest.of(0, 20), 1);
            when(emoticonMasterRepository.findPurchasedEmoticons(eq(1L), any(Pageable.class))).thenReturn(page);

            Page<EmoticonMasterDto> result = emoticonService.getPurchasedEmoticons(1L, PageRequest.of(0, 20));

            assertThat(result.getContent()).hasSize(1);
            verify(emoticonMasterRepository).findPurchasedEmoticons(eq(1L), any(Pageable.class));
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
        @DisplayName("구매 상태 조회는 구매 여부와 가격을 함께 반환한다")
        void getPurchaseStatus_authenticated() {
            when(emoticonMasterRepository.canUseEmoticon(1L, 1L)).thenReturn(true);

            EmoticonPurchaseStatusResponse result = emoticonService.getPurchaseStatus(1L, 1L);

            assertThat(result.isPurchased()).isTrue();
            assertThat(result.isAvailable()).isTrue();
            assertThat(result.getPrice()).isEqualTo(250);
            verify(emoticonMasterRepository).canUseEmoticon(1L, 1L);
            verify(shopItemRepository).findByItemTypeAndTargetId("EMOTICON", 1L);
        }

        @Test
        @DisplayName("비인증 구매 상태 조회는 가격만 조회하고 purchased false를 반환한다")
        void getPurchaseStatus_anonymous() {
            EmoticonPurchaseStatusResponse result = emoticonService.getPurchaseStatus(null, 1L);

            assertThat(result.isPurchased()).isFalse();
            assertThat(result.isAvailable()).isTrue();
            assertThat(result.getPrice()).isEqualTo(250);
            verify(emoticonMasterRepository, never()).canUseEmoticon(anyLong(), anyLong());
            verify(shopItemRepository).findByItemTypeAndTargetId("EMOTICON", 1L);
        }

        @Test
        @DisplayName("비활성 상품 구매 상태는 저장 가격과 available false를 반환한다")
        void getPurchaseStatus_inactiveItem() {
            emoticonShopItem.deactivate();

            EmoticonPurchaseStatusResponse result = emoticonService.getPurchaseStatus(null, 1L);

            assertThat(result.isPurchased()).isFalse();
            assertThat(result.isAvailable()).isFalse();
            assertThat(result.getPrice()).isEqualTo(250);
        }

        @Test
        @DisplayName("상품이 없으면 현재 설정 가격과 available false를 반환한다")
        void getPurchaseStatus_missingItem() {
            when(shopItemRepository.findByItemTypeAndTargetId("EMOTICON", 1L)).thenReturn(List.of());
            when(globalConfigService.getConfigFresh(GlobalConfigService.NOBICON_PRICE_CONFIG_KEY)).thenReturn("0");

            EmoticonPurchaseStatusResponse result = emoticonService.getPurchaseStatus(null, 1L);

            assertThat(result.isAvailable()).isFalse();
            assertThat(result.getPrice()).isZero();
        }

        @Test
        @DisplayName("상품이 없고 설정이 누락되거나 비정상이면 기본 가격 100을 반환한다")
        void getPurchaseStatus_missingItemUsesDefaultForInvalidConfig() {
            when(shopItemRepository.findByItemTypeAndTargetId("EMOTICON", 1L)).thenReturn(List.of());

            for (String configValue : new String[] {null, "invalid", "-1"}) {
                when(globalConfigService.getConfigFresh(GlobalConfigService.NOBICON_PRICE_CONFIG_KEY))
                        .thenReturn(configValue);

                EmoticonPurchaseStatusResponse result = emoticonService.getPurchaseStatus(null, 1L);

                assertThat(result.isAvailable()).isFalse();
                assertThat(result.getPrice()).isEqualTo(100);
            }
        }

    }

    private static void assertDefaultForbiddenException(Throwable ex) {
        BusinessException businessException = (BusinessException) ex;
        assertThat(businessException.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
        assertThat(businessException.getMessage()).isEqualTo(ErrorCode.FORBIDDEN.getMessage());
    }

    private java.util.ArrayList<EmoticonImage> emoticonImages(int count) {
        java.util.ArrayList<EmoticonImage> images = new java.util.ArrayList<>();
        for (int index = 0; index < count; index++) {
            EmoticonImage image = EmoticonImage.builder()
                    .emoticonMaster(emoticonMaster)
                    .imageUrl("/api/v1/files/" + (100 + index))
                    .sortOrder(index)
                    .build();
            ReflectionTestUtils.setField(image, "imageId", (long) (100 + index));
            images.add(image);
        }
        return images;
    }
}

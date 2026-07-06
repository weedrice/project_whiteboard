package com.weedrice.whiteboard.domain.emoticon.service;

import com.weedrice.whiteboard.domain.emoticon.dto.EmoticonMasterDto;
import com.weedrice.whiteboard.domain.emoticon.entity.EmoticonMaster;
import com.weedrice.whiteboard.domain.emoticon.repository.EmoticonMasterRepository;
import com.weedrice.whiteboard.domain.emoticon.repository.EmoticonSearchCondition;
import com.weedrice.whiteboard.domain.emoticon.repository.EmoticonSearchCondition.SearchType;
import com.weedrice.whiteboard.domain.emoticon.repository.EmoticonSearchCondition.SortType;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.hibernate.proxy.HibernateProxy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

class EmoticonCatalogService {

    private static final String SEARCH_TYPE_NAME = "NAME";
    private static final String SEARCH_TYPE_CREATOR = "CREATOR";
    private static final String SEARCH_TYPE_TAG = "TAG";
    private static final String SEARCH_TYPE_ALL = "ALL";
    private static final String SORT_BY_POPULAR = "popular";
    private static final String SORT_BY_OLDEST = "oldest";
    private static final Set<String> SEARCH_TYPES = Set.of(
            SEARCH_TYPE_NAME,
            SEARCH_TYPE_CREATOR,
            SEARCH_TYPE_TAG,
            SEARCH_TYPE_ALL
    );

    private final EmoticonMasterRepository emoticonMasterRepository;
    private final UserRepository userRepository;

    EmoticonCatalogService(EmoticonMasterRepository emoticonMasterRepository,
                           UserRepository userRepository) {
        this.emoticonMasterRepository = emoticonMasterRepository;
        this.userRepository = userRepository;
    }

    Page<EmoticonMasterDto> getActiveEmoticons(Pageable pageable) {
        return toSummaryPage(emoticonMasterRepository.findAllActive(pageable));
    }

    Page<EmoticonMasterDto> searchByTag(String tag, Pageable pageable) {
        return toSummaryPage(emoticonMasterRepository.findByTag(
                EmoticonRequestNormalizer.normalizeRequiredTag(tag),
                pageable));
    }

    Page<EmoticonMasterDto> searchByKeyword(String keyword, Pageable pageable) {
        return toSummaryPage(emoticonMasterRepository.findByKeyword(
                EmoticonRequestNormalizer.normalizeRequiredKeyword(keyword),
                pageable));
    }

    Page<EmoticonMasterDto> getMyEmoticons(Long userId, Pageable pageable) {
        return toSummaryPage(emoticonMasterRepository.findByCreatorId(userId, pageable));
    }

    EmoticonMasterDto getEmoticonDetail(Long emoticonId, Long userId) {
        EmoticonMaster master = emoticonMasterRepository.findByIdWithImages(emoticonId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EMOTICON_NOT_FOUND));

        if (!"Y".equals(master.getIsActive()) && userId != null) {
            if (!master.isOwner(userId) && !emoticonMasterRepository.canUseEmoticon(userId, emoticonId)) {
                throw new BusinessException(ErrorCode.EMOTICON_NOT_FOUND);
            }
        } else if (!"Y".equals(master.getIsActive()) && userId == null) {
            throw new BusinessException(ErrorCode.EMOTICON_NOT_FOUND);
        }

        return EmoticonMasterDto.from(master);
    }

    Page<EmoticonMasterDto> getActiveEmoticons(Pageable pageable, String sortBy) {
        Page<EmoticonMaster> result;
        String normalizedSortBy = EmoticonRequestNormalizer.normalizeOption(sortBy);
        if (isPopularSort(normalizedSortBy)) {
            result = emoticonMasterRepository.findAllActiveOrderByPurchaseCount(pageable);
        } else if (isOldestSort(normalizedSortBy)) {
            result = emoticonMasterRepository.findAllActiveOrderByCreatedAtAsc(pageable);
        } else {
            result = emoticonMasterRepository.findAllActive(pageable);
        }
        return toSummaryPage(result);
    }

    List<EmoticonMasterDto> getPopularEmoticons(String period) {
        LocalDateTime startDate;
        LocalDateTime now = LocalDateTime.now();

        switch (normalizePeriod(period)) {
            case "weekly":
                startDate = now.minusDays(7);
                break;
            case "monthly":
                startDate = now.minusDays(30);
                break;
            case "daily":
            default:
                startDate = now.minusDays(1);
                break;
        }

        return toSummaryList(emoticonMasterRepository.findPopularEmoticons(startDate, 5));
    }

    Page<EmoticonMasterDto> searchAll(String keyword, String searchType, Pageable pageable, String sortBy) {
        String normalizedSearchType = normalizeSearchType(searchType);
        String normalizedKeyword = EmoticonRequestNormalizer.normalizeOptionalKeyword(keyword);
        if (normalizedKeyword == null) {
            return getActiveEmoticons(pageable, sortBy);
        }

        Page<EmoticonMaster> result = emoticonMasterRepository.searchActive(
                new EmoticonSearchCondition(normalizedKeyword, toSearchType(normalizedSearchType), normalizeSortType(sortBy)),
                pageable);
        return toSummaryPage(result);
    }

    private SortType normalizeSortType(String sortBy) {
        String normalizedSortBy = EmoticonRequestNormalizer.normalizeOption(sortBy);
        if (isPopularSort(normalizedSortBy)) {
            return SortType.POPULAR;
        }
        if (isOldestSort(normalizedSortBy)) {
            return SortType.OLDEST;
        }
        return SortType.LATEST;
    }

    private boolean isPopularSort(String sortBy) {
        return sortBy != null && SORT_BY_POPULAR.equalsIgnoreCase(sortBy.trim());
    }

    private boolean isOldestSort(String sortBy) {
        return sortBy != null && SORT_BY_OLDEST.equalsIgnoreCase(sortBy.trim());
    }

    private String normalizePeriod(String period) {
        String normalizedPeriod = EmoticonRequestNormalizer.normalizeOption(period);
        if (normalizedPeriod == null) {
            return "daily";
        }
        return normalizedPeriod.toLowerCase(Locale.ROOT);
    }

    private String normalizeSearchType(String searchType) {
        String normalizedOption = EmoticonRequestNormalizer.normalizeOption(searchType);
        if (normalizedOption == null) {
            return SEARCH_TYPE_ALL;
        }
        String normalizedSearchType = normalizedOption.toUpperCase(Locale.ROOT);
        if (!SEARCH_TYPES.contains(normalizedSearchType)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return normalizedSearchType;
    }

    private SearchType toSearchType(String normalizedSearchType) {
        return switch (normalizedSearchType) {
            case SEARCH_TYPE_NAME -> SearchType.NAME;
            case SEARCH_TYPE_CREATOR -> SearchType.CREATOR;
            case SEARCH_TYPE_TAG -> SearchType.TAG;
            case SEARCH_TYPE_ALL -> SearchType.ALL;
            default -> throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        };
    }

    Page<EmoticonMasterDto> getPurchasedEmoticons(Long userId, Pageable pageable) {
        return toSummaryPage(emoticonMasterRepository.findPurchasedEmoticons(userId, pageable));
    }

    boolean hasPurchased(Long userId, Long emoticonId) {
        return emoticonMasterRepository.canUseEmoticon(userId, emoticonId);
    }

    private Page<EmoticonMasterDto> toSummaryPage(Page<EmoticonMaster> page) {
        Map<Long, String> creatorNamesById = loadCreatorNames(page.getContent());
        return page.map(master -> toSummaryDto(master, creatorNamesById));
    }

    private List<EmoticonMasterDto> toSummaryList(List<EmoticonMaster> masters) {
        Map<Long, String> creatorNamesById = loadCreatorNames(masters);
        return masters.stream()
                .map(master -> toSummaryDto(master, creatorNamesById))
                .collect(Collectors.toList());
    }

    private EmoticonMasterDto toSummaryDto(EmoticonMaster master, Map<Long, String> creatorNamesById) {
        Long creatorId = extractCreatorId(master);
        String creatorName = extractLoadedCreatorName(master);
        if (creatorName == null) {
            creatorName = creatorNamesById.get(creatorId);
        }
        return EmoticonMasterDto.fromWithoutImages(master, creatorId, creatorName);
    }

    private Map<Long, String> loadCreatorNames(Collection<EmoticonMaster> masters) {
        Set<Long> creatorIds = masters.stream()
                .filter(master -> extractLoadedCreatorName(master) == null)
                .map(this::extractCreatorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (creatorIds.isEmpty()) {
            return Map.of();
        }

        return userRepository.findAllById(creatorIds).stream()
                .collect(Collectors.toMap(User::getUserId, User::getDisplayName, (left, right) -> left));
    }

    private Long extractCreatorId(EmoticonMaster master) {
        User creator = master.getCreator();
        if (creator == null) {
            return null;
        }
        if (creator instanceof HibernateProxy proxy) {
            Object identifier = proxy.getHibernateLazyInitializer().getIdentifier();
            if (identifier instanceof Long creatorId) {
                return creatorId;
            }
        }
        return creator.getUserId();
    }

    private String extractLoadedCreatorName(EmoticonMaster master) {
        User creator = master.getCreator();
        if (creator == null) {
            return null;
        }
        if (creator instanceof HibernateProxy proxy && proxy.getHibernateLazyInitializer().isUninitialized()) {
            return null;
        }
        return creator.getDisplayName();
    }
}

package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.post.dto.PostSeriesNavigation;
import com.weedrice.whiteboard.domain.post.dto.PostSeriesRequest;
import com.weedrice.whiteboard.domain.post.dto.PostSeriesResponse;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.entity.PostSeries;
import com.weedrice.whiteboard.domain.post.entity.PostSeriesItem;
import com.weedrice.whiteboard.domain.post.repository.PostSeriesItemRepository;
import com.weedrice.whiteboard.domain.post.repository.PostSeriesRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.service.UserWritableResolver;
import com.weedrice.whiteboard.global.common.util.TextInputNormalizer;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostSeriesService {
    private final PostSeriesRepository postSeriesRepository;
    private final PostSeriesItemRepository postSeriesItemRepository;
    private final UserWritableResolver userWritableResolver;
    private final PostReadContextResolver postReadContextResolver;
    private final PostAccessPolicy postAccessPolicy;

    public List<PostSeriesResponse> getMySeries(@NonNull Long userId) {
        userWritableResolver.resolve(userId);
        return PostSeriesResponse.listFrom(postSeriesRepository.findByOwner_UserIdOrderBySeriesIdDesc(userId));
    }

    @Transactional
    public PostSeriesResponse createSeries(@NonNull Long userId, PostSeriesRequest request) {
        User owner = userWritableResolver.resolveForUpdate(userId);
        PostSeries series = PostSeries.builder()
                .owner(owner)
                .title(normalizeTitle(request.getTitle()))
                .description(TextInputNormalizer.normalizeOptional(request.getDescription(), 500))
                .build();
        return PostSeriesResponse.from(postSeriesRepository.save(series));
    }

    @Transactional
    public PostSeriesResponse updateSeries(@NonNull Long userId, @NonNull Long seriesId, PostSeriesRequest request) {
        userWritableResolver.resolveForUpdate(userId);
        PostSeries series = getOwnedSeriesForUpdate(userId, seriesId);
        series.update(normalizeTitle(request.getTitle()),
                TextInputNormalizer.normalizeOptional(request.getDescription(), 500));
        return PostSeriesResponse.from(series);
    }

    @Transactional
    public void deleteSeries(@NonNull Long userId, @NonNull Long seriesId) {
        userWritableResolver.resolveForUpdate(userId);
        postSeriesRepository.delete(getOwnedSeriesForUpdate(userId, seriesId));
    }

    @Transactional
    public void attachPostToSeries(@NonNull Long ownerUserId, @NonNull Post post, Long seriesId) {
        if (seriesId == null) {
            return;
        }
        userWritableResolver.resolveForUpdate(ownerUserId);
        movePostToSeries(ownerUserId, post, seriesId);
    }

    @Transactional
    public void updatePostSeries(@NonNull Long ownerUserId, @NonNull Post post, Long seriesId) {
        userWritableResolver.resolveForUpdate(ownerUserId);
        PostSeriesItem item = postSeriesItemRepository.findByPost_PostIdAndSeries_Owner_UserId(
                        post.getPostId(), ownerUserId)
                .orElse(null);
        if (seriesId == null) {
            if (item != null) {
                lockOwnedSeries(ownerUserId, List.of(item.getSeries().getSeriesId()));
                postSeriesItemRepository.delete(item);
            }
            return;
        }
        movePostToSeries(ownerUserId, post, seriesId, item);
    }

    private void movePostToSeries(Long ownerUserId, Post post, Long seriesId) {
        PostSeriesItem item = postSeriesItemRepository.findByPost_PostIdAndSeries_Owner_UserId(
                        post.getPostId(), ownerUserId)
                .orElse(null);
        movePostToSeries(ownerUserId, post, seriesId, item);
    }

    private void movePostToSeries(Long ownerUserId, Post post, Long seriesId, PostSeriesItem item) {
        List<Long> seriesIds = item == null
                ? List.of(seriesId)
                : java.util.stream.Stream.of(item.getSeries().getSeriesId(), seriesId)
                        .distinct()
                        .sorted()
                        .toList();
        Map<Long, PostSeries> lockedSeries = lockOwnedSeries(ownerUserId, seriesIds);
        PostSeries series = lockedSeries.get(seriesId);
        if (series == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        if (item != null && Objects.equals(item.getSeries().getSeriesId(), seriesId)) {
            return;
        }
        int nextSortOrder = postSeriesItemRepository.findMaxSortOrder(seriesId) + 1;
        if (item == null) {
            postSeriesItemRepository.save(PostSeriesItem.builder()
                    .series(series)
                    .post(post)
                    .sortOrder(nextSortOrder)
                    .build());
            return;
        }
        item.moveTo(series, nextSortOrder);
    }

    private Map<Long, PostSeries> lockOwnedSeries(Long ownerUserId, List<Long> seriesIds) {
        Map<Long, PostSeries> lockedSeries = postSeriesRepository
                .findAllOwnedByIdsForUpdate(ownerUserId, seriesIds)
                .stream()
                .collect(Collectors.toMap(PostSeries::getSeriesId, Function.identity()));
        if (lockedSeries.size() != seriesIds.size()) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return lockedSeries;
    }

    PostSeriesNavigation getNavigation(@NonNull Post post, PostReadContext readContext) {
        return postSeriesItemRepository.findByPost_PostId(post.getPostId())
                .map(currentItem -> {
                    List<PostSeriesItem> items = postSeriesItemRepository
                            .findBySeries_SeriesIdOrderBySortOrderAscItemIdAsc(currentItem.getSeries().getSeriesId());
                    List<Post> posts = items.stream().map(PostSeriesItem::getPost).toList();
                    PostReadContext context = postReadContextResolver.withAdminBoardIdsForPosts(
                            readContext == null ? PostReadContext.anonymous() : readContext,
                            posts);
                    List<PostSeriesItem> readableItems = items.stream()
                            .filter(item -> postAccessPolicy.isReadable(
                                    item.getPost(),
                                    context.viewer(),
                                    context.isAuthorBlocked(item.getPost()),
                                    context.activeAdminBoardIds()))
                            .toList();
                    int index = -1;
                    for (int i = 0; i < readableItems.size(); i++) {
                        if (readableItems.get(i).getPost().getPostId().equals(post.getPostId())) {
                            index = i;
                            break;
                        }
                    }
                    if (index < 0) {
                        return null;
                    }
                    Post previous = index > 0 ? readableItems.get(index - 1).getPost() : null;
                    Post next = index < readableItems.size() - 1 ? readableItems.get(index + 1).getPost() : null;
                    return PostSeriesNavigation.builder()
                            .series(PostSeriesNavigation.seriesFrom(
                                    currentItem.getSeries(), index + 1, readableItems.size()))
                            .previousPost(PostSeriesNavigation.postFrom(previous))
                            .nextPost(PostSeriesNavigation.postFrom(next))
                            .build();
                })
                .orElse(null);
    }

    private PostSeries getOwnedSeriesForUpdate(Long userId, Long seriesId) {
        return postSeriesRepository.findBySeriesIdAndOwnerUserIdForUpdate(seriesId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private String normalizeTitle(String title) {
        String normalizedTitle = TextInputNormalizer.normalizeOptional(title, 120);
        if (normalizedTitle == null || normalizedTitle.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return normalizedTitle;
    }
}

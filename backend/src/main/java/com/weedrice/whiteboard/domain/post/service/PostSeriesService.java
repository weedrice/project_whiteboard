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
import java.util.Objects;
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
        User owner = userWritableResolver.resolve(userId);
        PostSeries series = PostSeries.builder()
                .owner(owner)
                .title(normalizeTitle(request.getTitle()))
                .description(TextInputNormalizer.normalizeOptional(request.getDescription(), 500))
                .build();
        return PostSeriesResponse.from(postSeriesRepository.save(series));
    }

    @Transactional
    public PostSeriesResponse updateSeries(@NonNull Long userId, @NonNull Long seriesId, PostSeriesRequest request) {
        PostSeries series = getOwnedSeries(userId, seriesId);
        series.update(normalizeTitle(request.getTitle()),
                TextInputNormalizer.normalizeOptional(request.getDescription(), 500));
        return PostSeriesResponse.from(series);
    }

    @Transactional
    public void deleteSeries(@NonNull Long userId, @NonNull Long seriesId) {
        postSeriesRepository.delete(getOwnedSeries(userId, seriesId));
    }

    @Transactional
    public void attachPostToSeries(@NonNull Long ownerUserId, @NonNull Post post, Long seriesId) {
        if (seriesId == null) {
            return;
        }
        attachPostToLockedSeries(ownerUserId, post, seriesId);
    }

    @Transactional
    public void updatePostSeries(@NonNull Long ownerUserId, @NonNull Post post, Long seriesId) {
        if (seriesId == null) {
            postSeriesItemRepository.findByPost_PostIdAndSeries_Owner_UserId(post.getPostId(), ownerUserId)
                    .ifPresent(postSeriesItemRepository::delete);
            return;
        }
        attachPostToLockedSeries(ownerUserId, post, seriesId);
    }

    private void attachPostToLockedSeries(Long ownerUserId, Post post, Long seriesId) {
        PostSeries series = getOwnedSeriesForUpdate(ownerUserId, seriesId);
        PostSeriesItem item = postSeriesItemRepository.findByPost_PostIdAndSeries_Owner_UserId(
                        post.getPostId(), ownerUserId)
                .orElse(null);
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

    private PostSeries getOwnedSeries(Long userId, Long seriesId) {
        return postSeriesRepository.findBySeriesIdAndOwner_UserId(seriesId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
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

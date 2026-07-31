package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.board.constant.BoardPolicyConstants;
import com.weedrice.whiteboard.domain.post.constant.ScrapConstraints;
import com.weedrice.whiteboard.domain.post.dto.ScrapFolderRequest;
import com.weedrice.whiteboard.domain.post.dto.ScrapFolderResponse;
import com.weedrice.whiteboard.domain.post.dto.ScrapListResponse;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.entity.Scrap;
import com.weedrice.whiteboard.domain.post.entity.ScrapFolder;
import com.weedrice.whiteboard.domain.post.entity.ScrapId;
import com.weedrice.whiteboard.domain.post.repository.ScrapFolderRepository;
import com.weedrice.whiteboard.domain.post.repository.ScrapRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.common.service.ReactionWriter;
import com.weedrice.whiteboard.global.common.util.PageRequestUtils;
import com.weedrice.whiteboard.global.common.util.TextInputNormalizer;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostScrapService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final Sort DEFAULT_SORT = Sort.by(
            Sort.Order.desc("createdAt"),
            Sort.Order.desc("post.postId"));

    private final ScrapRepository scrapRepository;
    private final ScrapFolderRepository scrapFolderRepository;
    private final ReactionWriter reactionWriter;

    public boolean isScrappedBy(Long userId, Long postId) {
        return userId != null && scrapRepository.existsById(new ScrapId(userId, postId));
    }

    @Transactional
    public void scrap(User user, Post post, String remark, Long folderId) {
        String normalizedRemark = normalizeRemark(remark);
        ScrapFolder folder = folderId == null
                ? null
                : getOwnedScrapFolderForUpdate(user.getUserId(), folderId);
        Scrap scrap = Scrap.builder()
                .user(user)
                .post(post)
                .remark(normalizedRemark)
                .folder(folder)
                .build();
        reactionWriter.insertOrThrowDuplicate(
                () -> scrapRepository.saveAndFlush(scrap),
                ErrorCode.ALREADY_SCRAPED);
    }

    String normalizeRemark(String remark) {
        return TextInputNormalizer.normalizeOptional(remark, ScrapConstraints.MAX_REMARK_LENGTH);
    }

    @Transactional
    public void unscrap(Long userId, Long postId) {
        long deletedCount = scrapRepository.deleteByUser_UserIdAndPost_PostId(userId, postId);
        if (deletedCount == 0) {
            throw new BusinessException(ErrorCode.NOT_SCRAPED);
        }
    }

    @Transactional
    public void move(Long userId, Long postId, Long folderId) {
        Scrap scrap = scrapRepository.findOwnedByPostIdForUpdate(userId, postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_SCRAPED));
        ScrapFolder folder = folderId == null ? null : getOwnedScrapFolderForUpdate(userId, folderId);
        scrap.moveToFolder(folder);
    }

    public ScrapListResponse getMyScraps(
            Long userId,
            PostReadContext context,
            Long folderId,
            String keyword,
            Pageable pageable) {
        User user = context.viewer();
        if (folderId != null) {
            validateScrapFolderOwner(userId, folderId);
        }
        Pageable safePageable = PageRequestUtils.of(pageable, DEFAULT_PAGE_SIZE, DEFAULT_SORT);
        BlockedUserFilter blockedUsers = BlockedUserFilter.from(context.blockedUserIdSet());
        String normalizedKeyword = TextInputNormalizer.normalizeOptional(keyword, 255);
        Page<Scrap> scrapPage;
        if (normalizedKeyword == null) {
            scrapPage = folderId == null
                    ? scrapRepository.findPageByUserWithPostDetails(
                            user,
                            user.isUsableSuperAdmin(),
                            blockedUsers.empty(),
                            blockedUsers.ids(),
                            BoardPolicyConstants.INQUIRY_BOARD_URL,
                            safePageable)
                    : scrapRepository.findPageByUserWithPostDetails(
                            user,
                            folderId,
                            user.isUsableSuperAdmin(),
                            blockedUsers.empty(),
                            blockedUsers.ids(),
                            BoardPolicyConstants.INQUIRY_BOARD_URL,
                            safePageable);
        } else {
            scrapPage = scrapRepository.findPageByUserWithPostDetailsByKeyword(
                    user,
                    folderId,
                    toCaseInsensitiveLikePattern(normalizedKeyword),
                    user.isUsableSuperAdmin(),
                    blockedUsers.empty(),
                    blockedUsers.ids(),
                    BoardPolicyConstants.INQUIRY_BOARD_URL,
                    safePageable);
        }
        return ScrapListResponse.from(scrapPage);
    }

    public List<ScrapFolderResponse> getFolders(Long userId) {
        return ScrapFolderResponse.listFrom(
                scrapFolderRepository.findByUser_UserIdOrderBySortOrderAscFolderIdAsc(userId));
    }

    @Transactional
    public ScrapFolderResponse createFolder(User user, ScrapFolderRequest request) {
        Long userId = user.getUserId();
        String name = normalizeFolderName(request != null ? request.getName() : null);
        if (scrapFolderRepository.existsByUser_UserIdAndName(userId, name)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        ScrapFolder folder = ScrapFolder.builder()
                .user(user)
                .name(name)
                .sortOrder(request != null ? request.getSortOrder() : null)
                .build();
        try {
            return ScrapFolderResponse.from(scrapFolderRepository.saveAndFlush(folder));
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    @Transactional
    public ScrapFolderResponse updateFolder(Long userId, Long folderId, ScrapFolderRequest request) {
        ScrapFolder folder = getOwnedScrapFolderForUpdate(userId, folderId);
        String name = request == null ? null : TextInputNormalizer.normalizeOptional(request.getName(), 60);
        if (name != null && name.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (name != null && !name.equals(folder.getName())
                && scrapFolderRepository.existsByUser_UserIdAndName(userId, name)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        folder.update(name, request != null ? request.getSortOrder() : null);
        try {
            scrapFolderRepository.flush();
            return ScrapFolderResponse.from(folder);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    @Transactional
    public void deleteFolder(Long userId, Long folderId) {
        scrapFolderRepository.delete(getOwnedScrapFolderForUpdate(userId, folderId));
    }

    private String normalizeFolderName(String name) {
        String normalizedName = TextInputNormalizer.normalizeOptional(name, 60);
        if (normalizedName == null || normalizedName.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return normalizedName;
    }

    private String toCaseInsensitiveLikePattern(String keyword) {
        String escapedKeyword = keyword.toLowerCase(Locale.ROOT)
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        return "%" + escapedKeyword + "%";
    }

    private void validateScrapFolderOwner(Long userId, Long folderId) {
        if (scrapFolderRepository.findByFolderIdAndUser_UserId(folderId, userId).isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
    }

    private ScrapFolder getOwnedScrapFolderForUpdate(Long userId, Long folderId) {
        return scrapFolderRepository.findOwnedByIdForUpdate(folderId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private record BlockedUserFilter(boolean empty, List<Long> ids) {
        private static final List<Long> NO_BLOCKED_USER_IDS = List.of(-1L);

        static BlockedUserFilter from(Set<Long> blockedUserIds) {
            if (blockedUserIds == null || blockedUserIds.isEmpty()) {
                return new BlockedUserFilter(true, NO_BLOCKED_USER_IDS);
            }
            return new BlockedUserFilter(false, new ArrayList<>(blockedUserIds));
        }
    }
}

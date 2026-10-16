package com.weedrice.whiteboard.domain.user.service;

import com.weedrice.whiteboard.domain.user.entity.UserSettings;
import com.weedrice.whiteboard.domain.user.repository.UserSettingsRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProfilePrivacyPolicy {

    private final UserSettingsRepository userSettingsRepository;

    public Visibility resolve(Long targetUserId, Long viewerUserId) {
        UserSettings settings = userSettingsRepository.findById(targetUserId).orElse(null);
        boolean overviewPublic = settings == null || !Boolean.FALSE.equals(settings.getProfileOverviewPublic());
        boolean postsPublic = settings == null || !Boolean.FALSE.equals(settings.getProfilePostsPublic());
        boolean commentsPublic = settings == null || !Boolean.FALSE.equals(settings.getProfileCommentsPublic());
        boolean owner = targetUserId.equals(viewerUserId);
        return new Visibility(overviewPublic, postsPublic, commentsPublic,
                owner || overviewPublic, owner || postsPublic, owner || commentsPublic);
    }

    public void requireOverviewVisible(Long targetUserId, Long viewerUserId) {
        requireVisible(resolve(targetUserId, viewerUserId).canViewOverview());
    }

    public void requirePostsVisible(Long targetUserId, Long viewerUserId) {
        requireVisible(resolve(targetUserId, viewerUserId).canViewPosts());
    }

    public void requireCommentsVisible(Long targetUserId, Long viewerUserId) {
        requireVisible(resolve(targetUserId, viewerUserId).canViewComments());
    }

    private void requireVisible(boolean visible) {
        if (!visible) {
            throw new BusinessException(ErrorCode.PROFILE_SECTION_PRIVATE);
        }
    }

    public record Visibility(boolean profileOverviewPublic, boolean profilePostsPublic, boolean profileCommentsPublic,
                             boolean canViewOverview, boolean canViewPosts, boolean canViewComments) {
    }
}

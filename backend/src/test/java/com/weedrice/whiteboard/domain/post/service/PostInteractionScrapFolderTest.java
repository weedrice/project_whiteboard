package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.agent.service.AgentOwnershipService;
import com.weedrice.whiteboard.domain.badge.service.BadgeEvaluationService;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.post.dto.ScrapFolderRequest;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.entity.Scrap;
import com.weedrice.whiteboard.domain.post.entity.ScrapFolder;
import com.weedrice.whiteboard.domain.post.repository.PostLikeRepository;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.post.repository.ScrapFolderRepository;
import com.weedrice.whiteboard.domain.post.repository.ScrapRepository;
import com.weedrice.whiteboard.domain.post.repository.ViewHistoryRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.service.UserWritableResolver;
import com.weedrice.whiteboard.global.common.service.ReactionWriter;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostInteractionScrapFolderTest {

    @Mock PostRepository postRepository;
    @Mock PostLikeRepository postLikeRepository;
    @Mock PostReactionService postReactionService;
    @Mock ScrapRepository scrapRepository;
    @Mock ScrapFolderRepository scrapFolderRepository;
    @Mock ViewHistoryRepository viewHistoryRepository;
    @Mock ViewHistoryCommandService viewHistoryCommandService;
    @Mock CommentRepository commentRepository;
    @Mock PostReadContextResolver postReadContextResolver;
    @Mock AgentOwnershipService agentOwnershipService;
    @Mock UserWritableResolver userWritableResolver;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock PostSummaryAssembler postSummaryAssembler;
    @Mock PostAccessPolicy postAccessPolicy;
    @Mock ReactionWriter reactionWriter;
    @Mock PostViewCountWriter postViewCountWriter;
    @Mock EntityManager entityManager;
    @Mock BadgeEvaluationService badgeEvaluationService;

    @InjectMocks PostInteractionService service;

    @Test
    void createLocksUserAndMapsUniqueRaceToValidationError() {
        User user = User.builder().build();
        ScrapFolderRequest request = request("folder");
        when(userWritableResolver.resolveForUpdate(1L)).thenReturn(user);
        when(scrapFolderRepository.saveAndFlush(org.mockito.ArgumentMatchers.any(ScrapFolder.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> service.createScrapFolder(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);

        verify(userWritableResolver).resolveForUpdate(1L);
    }

    @Test
    void updateUsesLockedFolderAndMapsUniqueRaceToValidationError() {
        ScrapFolder folder = ScrapFolder.builder().user(User.builder().build()).name("old").build();
        when(scrapFolderRepository.findOwnedByIdForUpdate(2L, 1L)).thenReturn(Optional.of(folder));
        org.mockito.Mockito.doThrow(new DataIntegrityViolationException("duplicate"))
                .when(scrapFolderRepository).flush();

        assertThatThrownBy(() -> service.updateScrapFolder(1L, 2L, request("new")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);

        verify(scrapFolderRepository, never()).findByFolderIdAndUser_UserId(2L, 1L);
    }

    @Test
    void deleteUsesLockedOwnedFolder() {
        ScrapFolder folder = ScrapFolder.builder().user(User.builder().build()).name("folder").build();
        when(scrapFolderRepository.findOwnedByIdForUpdate(2L, 1L)).thenReturn(Optional.of(folder));

        service.deleteScrapFolder(1L, 2L);

        verify(scrapFolderRepository).delete(folder);
    }

    @Test
    void moveScrapUsesLockedOwnedScrapAndFolder() {
        User user = User.builder().build();
        Scrap scrap = Scrap.builder().user(user).post(Post.builder().build()).build();
        ScrapFolder folder = ScrapFolder.builder().user(user).name("folder").build();
        when(userWritableResolver.resolveForUpdate(1L)).thenReturn(user);
        when(scrapRepository.findOwnedByPostIdForUpdate(1L, 3L)).thenReturn(Optional.of(scrap));
        when(scrapFolderRepository.findOwnedByIdForUpdate(2L, 1L)).thenReturn(Optional.of(folder));

        service.moveScrap(1L, 3L, 2L);

        assertThat(scrap.getFolder()).isSameAs(folder);
        verify(userWritableResolver).resolveForUpdate(1L);
    }

    @Test
    void moveScrapToUnfiledDoesNotLoadFolder() {
        User user = User.builder().build();
        ScrapFolder currentFolder = ScrapFolder.builder().user(user).name("current").build();
        Scrap scrap = Scrap.builder().user(user).post(Post.builder().build()).folder(currentFolder).build();
        when(userWritableResolver.resolveForUpdate(1L)).thenReturn(user);
        when(scrapRepository.findOwnedByPostIdForUpdate(1L, 3L)).thenReturn(Optional.of(scrap));

        service.moveScrap(1L, 3L, null);

        assertThat(scrap.getFolder()).isNull();
        verify(scrapFolderRepository, never()).findOwnedByIdForUpdate(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void moveMissingScrapReturnsNotScraped() {
        when(userWritableResolver.resolveForUpdate(1L)).thenReturn(User.builder().build());
        when(scrapRepository.findOwnedByPostIdForUpdate(1L, 3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.moveScrap(1L, 3L, 2L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_SCRAPED);

        verify(scrapFolderRepository, never()).findOwnedByIdForUpdate(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong());
    }

    private ScrapFolderRequest request(String name) {
        ScrapFolderRequest request = new ScrapFolderRequest();
        org.springframework.test.util.ReflectionTestUtils.setField(request, "name", name);
        return request;
    }
}

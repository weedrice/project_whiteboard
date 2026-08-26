package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.post.dto.ScrapFolderRequest;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.entity.Scrap;
import com.weedrice.whiteboard.domain.post.entity.ScrapFolder;
import com.weedrice.whiteboard.domain.post.repository.ScrapFolderRepository;
import com.weedrice.whiteboard.domain.post.repository.ScrapRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.common.service.ReactionWriter;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostScrapServiceTest {

    @Mock ScrapRepository scrapRepository;
    @Mock ScrapFolderRepository scrapFolderRepository;
    @Mock ReactionWriter reactionWriter;
    @Mock com.weedrice.whiteboard.domain.inquiry.legacy.InquiryLegacyWritePolicy inquiryLegacyWritePolicy;

    @InjectMocks PostScrapService service;

    @Test
    void createMapsUniqueRaceToValidationError() {
        User user = User.builder().build();
        ScrapFolderRequest request = request("folder");
        when(scrapFolderRepository.saveAndFlush(org.mockito.ArgumentMatchers.any(ScrapFolder.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> service.createFolder(user, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    void updateUsesLockedFolderAndMapsUniqueRaceToValidationError() {
        ScrapFolder folder = ScrapFolder.builder().user(User.builder().build()).name("old").build();
        when(scrapFolderRepository.findOwnedByIdForUpdate(2L, 1L)).thenReturn(Optional.of(folder));
        org.mockito.Mockito.doThrow(new DataIntegrityViolationException("duplicate"))
                .when(scrapFolderRepository).flush();

        assertThatThrownBy(() -> service.updateFolder(1L, 2L, request("new")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);

        verify(scrapFolderRepository, never()).findByFolderIdAndUser_UserId(2L, 1L);
    }

    @Test
    void deleteUsesLockedOwnedFolder() {
        ScrapFolder folder = ScrapFolder.builder().user(User.builder().build()).name("folder").build();
        when(scrapFolderRepository.findOwnedByIdForUpdate(2L, 1L)).thenReturn(Optional.of(folder));

        service.deleteFolder(1L, 2L);

        verify(scrapFolderRepository).delete(folder);
    }

    @Test
    void moveUsesLockedOwnedScrapAndFolder() {
        User user = User.builder().build();
        Scrap scrap = Scrap.builder().user(user).post(Post.builder().build()).build();
        ScrapFolder folder = ScrapFolder.builder().user(user).name("folder").build();
        when(scrapRepository.findOwnedByPostIdForUpdate(1L, 3L)).thenReturn(Optional.of(scrap));
        when(scrapFolderRepository.findOwnedByIdForUpdate(2L, 1L)).thenReturn(Optional.of(folder));

        service.move(1L, 3L, 2L);

        assertThat(scrap.getFolder()).isSameAs(folder);
    }

    @Test
    void moveToUnfiledDoesNotLoadFolder() {
        User user = User.builder().build();
        ScrapFolder currentFolder = ScrapFolder.builder().user(user).name("current").build();
        Scrap scrap = Scrap.builder().user(user).post(Post.builder().build()).folder(currentFolder).build();
        when(scrapRepository.findOwnedByPostIdForUpdate(1L, 3L)).thenReturn(Optional.of(scrap));

        service.move(1L, 3L, null);

        assertThat(scrap.getFolder()).isNull();
        verify(scrapFolderRepository, never()).findOwnedByIdForUpdate(anyLong(), anyLong());
    }

    @Test
    void moveArchivedInquiryScrapIsRejectedBeforeFolderMutation() {
        User user = User.builder().build();
        com.weedrice.whiteboard.domain.board.entity.Board board =
                com.weedrice.whiteboard.domain.board.entity.Board.builder().boardUrl("inquiry").build();
        Scrap scrap = Scrap.builder()
                .user(user)
                .post(Post.builder().board(board).build())
                .build();
        when(scrapRepository.findOwnedByPostIdForUpdate(1L, 3L)).thenReturn(Optional.of(scrap));
        org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.LEGACY_INQUIRY_READ_ONLY))
                .when(inquiryLegacyWritePolicy).requireBoardWritable(board);

        assertThatThrownBy(() -> service.move(1L, 3L, 2L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.LEGACY_INQUIRY_READ_ONLY);

        verify(scrapFolderRepository, never()).findOwnedByIdForUpdate(anyLong(), anyLong());
    }

    @Test
    void moveMissingScrapReturnsNotScraped() {
        when(scrapRepository.findOwnedByPostIdForUpdate(1L, 3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.move(1L, 3L, 2L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_SCRAPED);

        verify(scrapFolderRepository, never()).findOwnedByIdForUpdate(anyLong(), anyLong());
    }

    private ScrapFolderRequest request(String name) {
        ScrapFolderRequest request = new ScrapFolderRequest();
        org.springframework.test.util.ReflectionTestUtils.setField(request, "name", name);
        return request;
    }
}

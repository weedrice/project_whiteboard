package com.weedrice.whiteboard.domain.board.service;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.file.service.FileService;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
class BoardIconAttachmentService {

    private final FileService fileService;

    BoardIconAttachmentService(FileService fileService) {
        this.fileService = fileService;
    }

    void syncBoardIcon(Long ownerUserId, Board board, String previousIconUrl) {
        Long currentFileId = FileService.extractFileIdFromUrl(board.getIconUrl());
        Long previousFileId = FileService.extractFileIdFromUrl(previousIconUrl);

        if (currentFileId != null && !Objects.equals(currentFileId, previousFileId)) {
            fileService.replaceBoardIcon(currentFileId, ownerUserId, board.getBoardId());
        }

        if (previousFileId != null && !Objects.equals(previousFileId, currentFileId)) {
            fileService.deleteFileWithStorageIfAssociated(
                    previousFileId,
                    board.getBoardId(),
                    FileService.RELATED_TYPE_BOARD_ICON);
        }
    }
}

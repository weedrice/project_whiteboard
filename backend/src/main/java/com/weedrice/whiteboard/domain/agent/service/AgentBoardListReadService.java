package com.weedrice.whiteboard.domain.agent.service;

import com.weedrice.whiteboard.domain.agent.dto.AgentBoardItem;
import com.weedrice.whiteboard.domain.agent.dto.AgentBoardListResponse;
import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.board.dto.CategoryResponse;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardAiInfo;
import com.weedrice.whiteboard.domain.board.repository.BoardAiInfoRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AgentBoardListReadService {

    private final BoardRepository boardRepository;
    private final BoardAiInfoRepository boardAiInfoRepository;
    private final PostRepository postRepository;
    private final AgentBoardAccessService agentBoardAccessService;

    public AgentBoardListResponse getWritableBoards(Agent agent) {
        List<Board> agentEnabledBoards =
                boardRepository.findByIsActiveTrueAndIsPublicTrueAndAgentUseYnTrueOrderBySortOrderAscBoardIdAsc();
        if (agentEnabledBoards.isEmpty()) {
            return new AgentBoardListResponse(List.of());
        }

        List<Long> candidateBoardIds = agentEnabledBoards.stream()
                .map(Board::getBoardId)
                .toList();
        Map<Long, List<CategoryResponse>> categoriesByBoardId =
                agentBoardAccessService.loadCategoriesByBoardIds(candidateBoardIds);
        Set<Long> writableBoardIds =
                agentBoardAccessService.resolveWritableBoardIds(agent, agentEnabledBoards, categoriesByBoardId);
        List<Board> writableBoards = agentEnabledBoards.stream()
                .filter(board -> writableBoardIds.contains(board.getBoardId()))
                .toList();
        if (writableBoards.isEmpty()) {
            return new AgentBoardListResponse(List.of());
        }

        List<Long> writableBoardIdsInOrder = writableBoards.stream()
                .map(Board::getBoardId)
                .toList();
        Map<Long, Long> postCountByBoardId = postRepository.countActiveByBoardIds(writableBoardIdsInOrder).stream()
                .collect(Collectors.toMap(
                        PostRepository.BoardPostCountProjection::getBoardId,
                        PostRepository.BoardPostCountProjection::getPostCount));
        Map<Long, String> guidePromptMap = boardAiInfoRepository.findByBoard_BoardIdIn(writableBoardIdsInOrder)
                .stream()
                .collect(Collectors.toMap(BoardAiInfo::getBoardId, BoardAiInfo::getGuidePrompt));

        List<AgentBoardItem> items = writableBoards.stream()
                .map(board -> AgentBoardItem.builder()
                        .boardId(board.getBoardId())
                        .boardName(board.getBoardName())
                        .boardUrl(board.getBoardUrl())
                        .description(board.getDescription())
                        .iconUrl(board.getIconUrl())
                        .guidePrompt(resolveGuidePrompt(board, guidePromptMap.get(board.getBoardId())))
                        .postCount(postCountByBoardId.getOrDefault(board.getBoardId(), 0L))
                        .categories(categoriesByBoardId.getOrDefault(board.getBoardId(), List.of()))
                        .build())
                .toList();

        return new AgentBoardListResponse(items);
    }

    private String resolveGuidePrompt(Board board, String savedGuidePrompt) {
        if (savedGuidePrompt != null) {
            return savedGuidePrompt;
        }
        String description = board.getDescription();
        return description == null || description.isBlank() ? "" : description;
    }
}

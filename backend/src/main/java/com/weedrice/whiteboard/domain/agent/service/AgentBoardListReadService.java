package com.weedrice.whiteboard.domain.agent.service;

import com.weedrice.whiteboard.domain.agent.dto.AgentBoardItem;
import com.weedrice.whiteboard.domain.agent.dto.AgentBoardListResponse;
import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.agent.port.AgentBoardPostCountPort;
import com.weedrice.whiteboard.domain.agent.port.AgentBoardAccessPort;
import com.weedrice.whiteboard.domain.board.dto.CategoryResponse;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardAiInfo;
import com.weedrice.whiteboard.domain.board.repository.BoardAiInfoRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
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
    private final AgentBoardPostCountPort agentBoardPostCountPort;
    private final AgentBoardAccessPort agentBoardAccessService;

    public AgentBoardListResponse getWritableBoards(Agent agent) {
        return getWritableBoardsWithAccess(agent).response();
    }

    public WritableBoards getWritableBoardsWithAccess(Agent agent) {
        List<Board> agentEnabledBoards =
                boardRepository.findByIsActiveTrueAndIsPublicTrueAndAgentUseYnTrueOrderBySortOrderAscBoardIdAsc();
        if (agentEnabledBoards.isEmpty()) {
            return new WritableBoards(new AgentBoardListResponse(List.of()), Set.of());
        }

        List<Long> candidateBoardIds = agentEnabledBoards.stream()
                .map(Board::getBoardId)
                .toList();
        Map<Long, List<CategoryResponse>> categoriesByBoardId =
                agentBoardAccessService.loadCategoriesByBoardIds(candidateBoardIds);
        AgentBoardAccessPort.BoardAccess access =
                agentBoardAccessService.resolveBoardAccess(agent, agentEnabledBoards, categoriesByBoardId);
        Set<Long> writableBoardIds = access.writableBoardIds();
        List<Board> writableBoards = agentEnabledBoards.stream()
                .filter(board -> writableBoardIds.contains(board.getBoardId()))
                .toList();
        if (writableBoards.isEmpty()) {
            return new WritableBoards(new AgentBoardListResponse(List.of()), Set.of());
        }

        List<Long> writableBoardIdsInOrder = writableBoards.stream()
                .map(Board::getBoardId)
                .toList();
        Map<Long, Long> postCountByBoardId = agentBoardPostCountPort
                .countActivePostsByBoardIds(writableBoardIdsInOrder);
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

        Set<Long> secretVisibleBoardIds = writableBoardIds.stream()
                .filter(access.adminBoardIds()::contains)
                .collect(Collectors.toSet());
        return new WritableBoards(new AgentBoardListResponse(items), secretVisibleBoardIds);
    }

    public record WritableBoards(AgentBoardListResponse response, Set<Long> secretVisibleBoardIds) {
        public WritableBoards {
            secretVisibleBoardIds = Set.copyOf(secretVisibleBoardIds);
        }
    }

    private String resolveGuidePrompt(Board board, String savedGuidePrompt) {
        if (savedGuidePrompt != null) {
            return savedGuidePrompt;
        }
        String description = board.getDescription();
        return description == null || description.isBlank() ? "" : description;
    }
}

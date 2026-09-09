package com.weedrice.whiteboard.domain.agent.port;

import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.board.dto.CategoryResponse;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardCategory;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Consumer-owned boundary for board authorization that requires owner data. */
public interface AgentBoardAccessPort {

    Set<Long> resolveWritableBoardIds(
            Agent agent, List<Board> boards, Map<Long, List<CategoryResponse>> categoriesByBoardId);

    Set<Long> resolveBoardAdminIds(Agent agent, List<Board> boards, List<Long> boardIds);

    void validateAgentBoardWritable(Agent agent, Board board);

    void validateAgentBoardWritable(Agent agent, Board board, BoardCategory category);

    void validateAgentBoardReadable(Agent agent, Board board);

    boolean canViewSecretPosts(Agent agent, Board board);

    List<Board> getAccessibleFeedBoards(Agent agent, Long boardId);

    Map<Long, List<CategoryResponse>> loadCategoriesByBoardIds(List<Long> boardIds);
}

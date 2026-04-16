package com.weedrice.whiteboard.domain.board.service;

import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.board.dto.BoardCreateRequest;
import com.weedrice.whiteboard.domain.board.dto.BoardResponse;
import com.weedrice.whiteboard.domain.board.dto.BoardUpdateRequest;
import com.weedrice.whiteboard.domain.board.dto.CategoryRequest;
import com.weedrice.whiteboard.domain.board.dto.CategoryResponse;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.repository.BoardAiInfoRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardCategoryRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardSubscriptionRepository;
import com.weedrice.whiteboard.domain.point.service.PointService;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.common.service.GlobalConfigService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class BoardService {

    private final BoardQueryService queryService;
    private final BoardProvisioningService provisioningService;
    private final BoardSubscriptionService subscriptionService;
    private final BoardCategoryService categoryService;

    public BoardService(BoardRepository boardRepository,
                        BoardAiInfoRepository boardAiInfoRepository,
                        BoardCategoryRepository boardCategoryRepository,
                        BoardSubscriptionRepository boardSubscriptionRepository,
                        UserRepository userRepository,
                        AdminRepository adminRepository,
                        PointService pointService,
                        GlobalConfigService globalConfigService,
                        BoardResponseAssembler boardResponseAssembler,
                        BoardAccessPolicy boardAccessPolicy) {
        this.queryService = new BoardQueryService(
                boardRepository,
                boardCategoryRepository,
                boardSubscriptionRepository,
                userRepository,
                boardResponseAssembler,
                boardAccessPolicy);
        this.provisioningService = new BoardProvisioningService(
                boardRepository,
                boardAiInfoRepository,
                boardCategoryRepository,
                userRepository,
                adminRepository,
                pointService,
                globalConfigService);
        this.subscriptionService = new BoardSubscriptionService(
                boardRepository,
                boardSubscriptionRepository,
                userRepository,
                boardAccessPolicy);
        this.categoryService = new BoardCategoryService(boardRepository, boardCategoryRepository);
    }

    public List<BoardResponse> getActiveBoards(UserDetails userDetails) {
        return queryService.getActiveBoards(userDetails);
    }

    public List<BoardResponse> getTopBoards(UserDetails userDetails) {
        return queryService.getTopBoards(userDetails);
    }

    public List<BoardResponse> getAllBoards(UserDetails userDetails) {
        return queryService.getAllBoards(userDetails);
    }

    public BoardResponse getBoardDetails(String boardUrl, UserDetails userDetails) {
        return queryService.getBoardDetails(boardUrl, userDetails);
    }

    public List<CategoryResponse> getActiveCategories(String boardUrl, UserDetails userDetails) {
        return queryService.getActiveCategories(boardUrl, userDetails);
    }

    @Transactional
    public void ensureInquiryBoard(UserDetails userDetails, String requestedBoardUrl) {
        provisioningService.ensureInquiryBoard(userDetails, requestedBoardUrl);
    }

    @Transactional
    public void subscribeBoard(Long userId, String boardUrl) {
        subscriptionService.subscribeBoard(userId, boardUrl);
    }

    @Transactional
    public void unsubscribeBoard(Long userId, String boardUrl) {
        subscriptionService.unsubscribeBoard(userId, boardUrl);
    }

    public Page<BoardResponse> getMySubscriptions(Long userId, Pageable pageable) {
        return queryService.getMySubscriptions(userId, pageable);
    }

    @Transactional
    public Board createBoard(Long creatorId, BoardCreateRequest request) {
        return provisioningService.createBoard(creatorId, request);
    }

    @Transactional
    public Board updateBoard(String boardUrl, BoardUpdateRequest request, UserDetails userDetails) {
        return provisioningService.updateBoard(boardUrl, request, userDetails);
    }

    @Transactional
    public void transferBoardManager(String boardUrl, String loginId, UserDetails userDetails) {
        provisioningService.transferBoardManager(boardUrl, loginId, userDetails);
    }

    @Transactional
    public void deleteBoard(String boardUrl, UserDetails userDetails) {
        provisioningService.deleteBoard(boardUrl, userDetails);
    }

    @Transactional
    public CategoryResponse createCategory(String boardUrl, CategoryRequest request) {
        return categoryService.createCategory(boardUrl, request);
    }

    @Transactional
    public CategoryResponse updateCategory(Long categoryId, CategoryRequest request) {
        return categoryService.updateCategory(categoryId, request);
    }

    @Transactional
    public void deleteCategory(Long categoryId) {
        categoryService.deleteCategory(categoryId);
    }

    @Transactional
    public void updateSubscriptionOrder(Long userId, List<String> boardUrls) {
        subscriptionService.updateSubscriptionOrder(userId, boardUrls);
    }
}

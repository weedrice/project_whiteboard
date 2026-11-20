package com.weedrice.whiteboard.domain.board.service;

import com.weedrice.whiteboard.domain.board.dto.CategoryResponse;
import com.weedrice.whiteboard.domain.board.dto.CategoryRequest;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardCategory;
import com.weedrice.whiteboard.domain.board.repository.BoardCategoryRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BoardCategoryServiceTest {

    @Mock
    private BoardCategoryMutationResolver mutationResolver;
    @Mock
    private BoardCategoryRepository boardCategoryRepository;
    @Mock
    private BoardCategoryNameConflictPolicy nameConflictPolicy;
    private BoardCategoryService service;
    private Board board;
    private BoardCategory defaultCategory;
    private BoardCategory categoryA;
    private BoardCategory categoryB;

    @BeforeEach
    void setUp() {
        service = new BoardCategoryService(
                mutationResolver,
                boardCategoryRepository,
                nameConflictPolicy,
                new BoardCategoryDefaultCommand(boardCategoryRepository),
                new BoardCategoryResponseAssembler());
        board = Board.builder().boardName("Free").build();
        ReflectionTestUtils.setField(board, "boardId", 10L);
        defaultCategory = category(1L, "General", 1, true);
        categoryA = category(2L, "A", 2, false);
        categoryB = category(3L, "B", 3, false);

        lenient().when(mutationResolver.resolveBoardForUpdate("free", 7L)).thenReturn(board);
        lenient().when(boardCategoryRepository.findByBoard_BoardIdAndIsActiveOrderBySortOrderAsc(10L, true))
                .thenReturn(List.of(defaultCategory, categoryA, categoryB));
    }

    @Test
    void reorderCategories_updatesEveryActiveCategoryInRequestedOrder() {
        List<CategoryResponse> responses = service.reorderCategories("free", List.of(1L, 3L, 2L), 7L);

        assertThat(responses).extracting(CategoryResponse::getCategoryId).containsExactly(1L, 3L, 2L);
        assertThat(responses).extracting(CategoryResponse::getSortOrder).containsExactly(1, 2, 3);
        assertThat(defaultCategory.getSortOrder()).isEqualTo(1);
        assertThat(categoryB.getSortOrder()).isEqualTo(2);
        assertThat(categoryA.getSortOrder()).isEqualTo(3);
        verify(mutationResolver).resolveBoardForUpdate("free", 7L);
    }

    @Test
    void reorderCategories_rejectsDuplicateIdsWithoutMutation() {
        assertValidationError(List.of(1L, 2L, 2L));
    }

    @Test
    void reorderCategories_rejectsMissingOrForeignIdsWithoutMutation() {
        assertValidationError(List.of(1L, 2L));
        assertValidationError(List.of(1L, 2L, 99L));
    }

    @Test
    void reorderCategories_requiresDefaultCategoryFirstWithoutMutation() {
        assertValidationError(List.of(2L, 1L, 3L));
    }

    @Test
    void reorderCategories_allowsAnyCompleteOrderForLegacyBoardWithoutDefault() {
        when(boardCategoryRepository.findByBoard_BoardIdAndIsActiveOrderBySortOrderAsc(10L, true))
                .thenReturn(List.of(categoryA, categoryB));

        List<CategoryResponse> responses = service.reorderCategories("free", List.of(3L, 2L), 7L);

        assertThat(responses).extracting(CategoryResponse::getCategoryId).containsExactly(3L, 2L);
        assertThat(categoryB.getSortOrder()).isEqualTo(1);
        assertThat(categoryA.getSortOrder()).isEqualTo(2);
    }

    @ParameterizedTest
    @CsvSource({"3,4", "2147483647,2147483647"})
    void createCategory_appendsEvenWhenTheMaximumRankIsAlreadyUsed(int maximum, int expected) {
        CategoryRequest request = categoryRequest("New", 3);
        when(mutationResolver.resolveBoardForCreate("free", 7L)).thenReturn(board);
        when(boardCategoryRepository.findMaxActiveSortOrder(10L)).thenReturn(maximum);
        when(boardCategoryRepository.saveAndFlush(any(BoardCategory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CategoryResponse response = service.createCategory("free", request, 7L);

        assertThat(response.getSortOrder()).isEqualTo(expected);
        verify(boardCategoryRepository).findMaxActiveSortOrder(10L);
    }

    @Test
    void createCategory_preservesRequestedOrderWhenItIsAfterCurrentMaximum() {
        CategoryRequest request = categoryRequest("New", 10);
        when(mutationResolver.resolveBoardForCreate("free", 7L)).thenReturn(board);
        when(boardCategoryRepository.findMaxActiveSortOrder(10L)).thenReturn(3);
        when(boardCategoryRepository.saveAndFlush(any(BoardCategory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CategoryResponse response = service.createCategory("free", request, 7L);

        assertThat(response.getSortOrder()).isEqualTo(10);
    }

    @Test
    void editingInactiveLegacyDefaultLeavesActiveDefaultAndOrderIntact() {
        BoardCategory archived = category(4L, "Legacy", 8, true);
        archived.deactivate();
        categoryB.updateSortOrder(7);

        update(archived, categoryRequest("Archived", 6));

        assertThat(archived.getSortOrder()).isEqualTo(6);
        assertThat(archived.getIsActive()).isFalse();
        assertThat(defaultCategory.isDefaultCategory()).isTrue();
        assertThat(defaultCategory.getSortOrder()).isOne();
        assertThat(categoryB.getSortOrder()).isEqualTo(7);
    }

    @Test
    void reselectingFirstDefaultRepairsDuplicateFlagsAndAllowsReordering() {
        categoryA.setDefaultCategory(true);
        categoryA.updateSortOrder(8);
        CategoryRequest request = categoryRequest("General", 1);
        ReflectionTestUtils.setField(request, "isDefault", true);

        update(defaultCategory, request);

        assertThat(defaultCategory.isDefaultCategory()).isTrue();
        assertThat(categoryA.isDefaultCategory()).isFalse();
        assertThat(categoryA.getSortOrder()).isEqualTo(2);
        assertThat(service.reorderCategories("free", List.of(1L, 3L, 2L), 7L))
                .extracting(CategoryResponse::getCategoryId).containsExactly(1L, 3L, 2L);
    }

    @Test
    void reselectingTheOnlyDefaultPreservesOtherStoredPositions() {
        categoryB.updateSortOrder(7);
        CategoryRequest request = categoryRequest("Renamed", 1);
        ReflectionTestUtils.setField(request, "isDefault", true);

        update(defaultCategory, request);

        assertThat(categoryB.getSortOrder()).isEqualTo(7);
    }

    @Test
    void ordinaryCategoryRequestingPositionOneRemainsAfterTheActiveDefault() {
        when(boardCategoryRepository.existsByBoard_BoardIdAndIsActiveTrueAndIsDefaultTrue(10L)).thenReturn(true);

        CategoryResponse response = update(categoryB, categoryRequest("Renamed", 1));

        assertThat(response.getSortOrder()).isEqualTo(2);
        assertThat(response.isDefault()).isFalse();
        assertThat(defaultCategory.getSortOrder()).isOne();
        assertThat(defaultCategory.isDefaultCategory()).isTrue();
    }

    private CategoryResponse update(BoardCategory category, CategoryRequest request) {
        when(mutationResolver.resolveCategoryForUpdate(category.getCategoryId(), 7L)).thenReturn(category);
        when(boardCategoryRepository.saveAndFlush(category)).thenReturn(category);
        return service.updateCategory(category.getCategoryId(), request, 7L);
    }

    private void assertValidationError(List<Long> categoryIds) {
        assertThatThrownBy(() -> service.reorderCategories("free", categoryIds, 7L))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        assertThat(defaultCategory.getSortOrder()).isEqualTo(1);
        assertThat(categoryA.getSortOrder()).isEqualTo(2);
        assertThat(categoryB.getSortOrder()).isEqualTo(3);
    }

    private BoardCategory category(Long id, String name, int sortOrder, boolean isDefault) {
        BoardCategory category = BoardCategory.builder()
                .board(board)
                .name(name)
                .sortOrder(sortOrder)
                .minWriteRole("USER")
                .isDefault(isDefault)
                .build();
        ReflectionTestUtils.setField(category, "categoryId", id);
        return category;
    }

    private CategoryRequest categoryRequest(String name, int sortOrder) {
        CategoryRequest request = new CategoryRequest();
        ReflectionTestUtils.setField(request, "name", name);
        ReflectionTestUtils.setField(request, "sortOrder", sortOrder);
        ReflectionTestUtils.setField(request, "minWriteRole", "USER");
        return request;
    }
}

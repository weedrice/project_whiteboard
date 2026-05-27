import { computed, ref, watch, type Ref } from 'vue'
import { useBoard } from '@/composables/useBoard'
import type { BoardDetail } from '@/types'

export interface BoardEditFormData {
    boardName: string
    boardUrl: string
    description: string
    iconUrl: string
    sortOrder: number
    allowNsfw: boolean
    isPublic: boolean
    agentUseYn: boolean
    guidePrompt: string
}

interface BoardEditResourceOptions {
    fallbackManagerLabel: string
    onForbidden: (boardUrl: string) => void
    onLoadError: (error: unknown, boardUrl: string) => void
}

export function createEmptyBoardEditForm(): BoardEditFormData {
    return {
        boardName: '',
        boardUrl: '',
        description: '',
        iconUrl: '',
        sortOrder: 0,
        allowNsfw: false,
        isPublic: true,
        agentUseYn: false,
        guidePrompt: ''
    }
}

export function toBoardEditForm(board: BoardDetail): BoardEditFormData {
    return {
        boardName: board.boardName,
        boardUrl: board.boardUrl,
        description: board.description || '',
        iconUrl: board.iconUrl || '',
        sortOrder: board.sortOrder || 0,
        allowNsfw: board.allowNsfw || false,
        isPublic: board.isPublic ?? true,
        agentUseYn: board.agentUseYn ?? false,
        guidePrompt: board.guidePrompt || ''
    }
}

export function useBoardEditResource(boardUrl: Ref<string>, options: BoardEditResourceOptions) {
    const { useBoardDetail } = useBoard()
    const boardQuery = useBoardDetail(boardUrl, { meta: { errorMessage: false } })

    const form = ref(createEmptyBoardEditForm())
    const error = ref('')
    const canManageBoard = ref(true)
    const currentManagerLabel = ref('')
    const handledForbiddenUrl = ref('')
    const handledErrorUrl = ref('')

    const isLoading = computed(() => Boolean(boardQuery.isLoading?.value || boardQuery.isFetching?.value))

    function resetBoardState() {
        form.value = createEmptyBoardEditForm()
        error.value = ''
        canManageBoard.value = true
        currentManagerLabel.value = ''
        handledForbiddenUrl.value = ''
        handledErrorUrl.value = ''
    }

    function applyBoard(board: BoardDetail) {
        if (!boardUrl.value || board.boardUrl !== boardUrl.value) return

        if (!board.isAdmin) {
            canManageBoard.value = false
            if (handledForbiddenUrl.value !== boardUrl.value) {
                handledForbiddenUrl.value = boardUrl.value
                options.onForbidden(boardUrl.value)
            }
            return
        }

        canManageBoard.value = true
        form.value = toBoardEditForm(board)
        currentManagerLabel.value = board.adminDisplayName || options.fallbackManagerLabel
    }

    watch(boardUrl, () => {
        resetBoardState()
    })

    watch(() => boardQuery.data.value, (board) => {
        if (board) {
            applyBoard(board)
        }
    }, { immediate: true })

    watch(() => boardQuery.error.value, (queryError) => {
        if (!queryError || !boardUrl.value || handledErrorUrl.value === boardUrl.value) return
        handledErrorUrl.value = boardUrl.value
        options.onLoadError(queryError, boardUrl.value)
    }, { immediate: true })

    return {
        form,
        error,
        canManageBoard,
        currentManagerLabel,
        isLoading,
        resetBoardState,
    }
}

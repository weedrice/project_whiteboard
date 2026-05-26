import { ref } from 'vue'
import { Storage } from '@/utils/storage'

const STORAGE_KEY = 'recentBoards'
const MAX_RECENT_BOARDS = 10

export interface RecentBoard {
    boardUrl: string
    boardName: string
    iconUrl?: string
    visitedAt: string
}

// 전역 상태 (composable 외부에 선언하여 싱글톤으로 관리)
const recentBoards = ref<RecentBoard[]>([])

function loadFromStorage() {
    const stored = Storage.get<RecentBoard[]>(STORAGE_KEY, [])
    recentBoards.value = stored || []
}

function saveToStorage() {
    Storage.set(STORAGE_KEY, recentBoards.value)
}

export function useRecentBoards() {
    // 초기 로드
    if (recentBoards.value.length === 0) {
        loadFromStorage()
    }

    /**
     * 게시판 방문 기록 추가/갱신
     */
    function addRecentBoard(board: { boardUrl: string; boardName: string; iconUrl?: string }) {
        // 기존 항목 제거 (중복 방지)
        const filtered = recentBoards.value.filter(b => b.boardUrl !== board.boardUrl)

        // 맨 앞에 추가
        filtered.unshift({
            boardUrl: board.boardUrl,
            boardName: board.boardName,
            iconUrl: board.iconUrl,
            visitedAt: new Date().toISOString(),
        })

        // 최대 개수 제한
        recentBoards.value = filtered.slice(0, MAX_RECENT_BOARDS)
        saveToStorage()
    }

    /**
     * 특정 게시판 기록 제거
     */
    function removeRecentBoard(boardUrl: string) {
        recentBoards.value = recentBoards.value.filter(b => b.boardUrl !== boardUrl)
        saveToStorage()
    }

    /**
     * 전체 기록 초기화
     */
    function clearRecentBoards() {
        recentBoards.value = []
        Storage.remove(STORAGE_KEY)
    }

    /**
     * 스토리지에서 다시 로드 (다른 탭에서 변경된 경우 등)
     */
    function refresh() {
        loadFromStorage()
    }

    return {
        recentBoards,
        addRecentBoard,
        removeRecentBoard,
        clearRecentBoards,
        refresh,
    }
}

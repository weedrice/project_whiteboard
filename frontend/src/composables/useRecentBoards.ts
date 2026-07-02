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

const recentBoards = ref<RecentBoard[]>([])

function isRecord(value: unknown): value is Record<string, unknown> {
    return typeof value === 'object' && value !== null
}

function normalizeRecentBoard(value: unknown): RecentBoard | null {
    if (!isRecord(value)) return null

    const boardUrl = typeof value.boardUrl === 'string' ? value.boardUrl.trim() : ''
    const boardName = typeof value.boardName === 'string' ? value.boardName.trim() : ''
    const visitedAt = typeof value.visitedAt === 'string' ? value.visitedAt : ''

    if (!boardUrl || !boardName || Number.isNaN(Date.parse(visitedAt))) {
        return null
    }

    const iconUrl = typeof value.iconUrl === 'string' && value.iconUrl.trim()
        ? value.iconUrl.trim()
        : undefined

    return {
        boardUrl,
        boardName,
        ...(iconUrl ? { iconUrl } : {}),
        visitedAt,
    }
}

export function normalizeRecentBoards(value: unknown): RecentBoard[] {
    if (!Array.isArray(value)) return []

    const seen = new Set<string>()
    const normalized: RecentBoard[] = []

    for (const item of value) {
        const board = normalizeRecentBoard(item)
        if (!board || seen.has(board.boardUrl)) {
            continue
        }

        seen.add(board.boardUrl)
        normalized.push(board)

        if (normalized.length >= MAX_RECENT_BOARDS) {
            break
        }
    }

    return normalized
}

function saveToStorage() {
    Storage.set(STORAGE_KEY, recentBoards.value)
}

function loadFromStorage() {
    const stored = Storage.get<unknown>(STORAGE_KEY, [])
    const normalized = normalizeRecentBoards(stored)
    recentBoards.value = normalized

    if (JSON.stringify(stored) !== JSON.stringify(normalized)) {
        saveToStorage()
    }
}

export function useRecentBoards() {
    if (recentBoards.value.length === 0) {
        loadFromStorage()
    }

    function addRecentBoard(board: { boardUrl: string; boardName: string; iconUrl?: string }) {
        const normalizedBoard = normalizeRecentBoard({
            ...board,
            visitedAt: new Date().toISOString(),
        })
        if (!normalizedBoard) return

        const filtered = recentBoards.value.filter(b => b.boardUrl !== normalizedBoard.boardUrl)
        filtered.unshift(normalizedBoard)

        recentBoards.value = normalizeRecentBoards(filtered)
        saveToStorage()
    }

    function removeRecentBoard(boardUrl: string) {
        recentBoards.value = recentBoards.value.filter(b => b.boardUrl !== boardUrl)
        saveToStorage()
    }

    function clearRecentBoards() {
        recentBoards.value = []
        Storage.remove(STORAGE_KEY)
    }

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

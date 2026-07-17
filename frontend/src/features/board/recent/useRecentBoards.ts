import { getCurrentScope, onScopeDispose, ref, watch } from 'vue'
import { Storage } from '@/utils/storage'
import { useAuthStore } from '@/stores/auth'

const STORAGE_KEY_PREFIX = 'recentBoards'
const MAX_RECENT_BOARDS = 10

export interface RecentBoard {
    boardUrl: string
    boardName: string
    iconUrl?: string
    visitedAt: string
}

const recentBoards = ref<RecentBoard[]>([])
let activeStorageKey: string | null = null

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
    if (activeStorageKey) {
        Storage.set(activeStorageKey, recentBoards.value)
    }
}

function loadFromStorage(storageKey: string | null) {
    activeStorageKey = storageKey
    if (!storageKey) {
        recentBoards.value = []
        return
    }

    const stored = Storage.get<unknown>(storageKey, [])
    const normalized = normalizeRecentBoards(stored)
    recentBoards.value = normalized

    if (JSON.stringify(stored) !== JSON.stringify(normalized)) {
        saveToStorage()
    }
}

export function useRecentBoards() {
    const authStore = useAuthStore()
    const resolveStorageKey = () => {
        if (!authStore.isAuthenticated) return `${STORAGE_KEY_PREFIX}:guest`
        if (authStore.user?.userId != null) return `${STORAGE_KEY_PREFIX}:user:${authStore.user.userId}`
        return null
    }

    // The former singleton key can contain another account's private board metadata.
    Storage.remove(STORAGE_KEY_PREFIX)
    loadFromStorage(resolveStorageKey())
    const stopSessionWatch = watch(
        () => [authStore.sessionGeneration, authStore.isAuthenticated, authStore.user?.userId] as const,
        () => loadFromStorage(resolveStorageKey()),
        { flush: 'sync' },
    )
    if (getCurrentScope()) {
        onScopeDispose(stopSessionWatch)
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
        if (activeStorageKey) {
            Storage.remove(activeStorageKey)
        }
    }

    function refresh() {
        loadFromStorage(resolveStorageKey())
    }

    return {
        recentBoards,
        addRecentBoard,
        removeRecentBoard,
        clearRecentBoards,
        refresh,
        stopSessionWatch,
    }
}

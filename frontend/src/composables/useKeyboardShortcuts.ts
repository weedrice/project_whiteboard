import { onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useKeyboardStore } from '@/stores/keyboard'
import { useAuthStore } from '@/stores/auth'
import { useThemePreference } from '@/composables/useThemePreference'
import { isInputFocused } from '@/utils/keyboard'

export interface KeyboardShortcutHandlers {
    // 드롭다운 토글 함수들
    toggleSubscriptionDropdown?: () => void
    toggleAllBoardsDropdown?: () => void
    toggleUserDropdown?: () => void
    toggleNotificationDropdown?: () => void
    logout?: () => void | Promise<void>
    isDropdownOpen?: () => boolean
    // 현재 드롭다운 닫기
    closeCurrentDropdown?: () => void
}

/**
 * 전역 키보드 단축키 관리 composable
 * DefaultLayout에서 사용
 */
export function useKeyboardShortcuts(handlers: KeyboardShortcutHandlers = {}) {
    const router = useRouter()
    const keyboardStore = useKeyboardStore()
    const authStore = useAuthStore()
    const { toggleTheme } = useThemePreference()

    const handleKeyDown = (event: KeyboardEvent) => {
        const { key, shiftKey, ctrlKey, altKey, metaKey } = event
        const isDropdownOpen = keyboardStore.isDropdownOpen || Boolean(handlers.isDropdownOpen?.())

        // 드롭다운 열린 상태에서는 특수 처리
        if (isDropdownOpen) {
            // ESC로 드롭다운 닫기
            if (key === 'Escape') {
                event.preventDefault()
                handlers.closeCurrentDropdown?.()
                keyboardStore.closeDropdown()
                return
            }

            // 숫자키로 항목 선택
            // 드롭다운 열린 상태에서는 다른 단축키 무시
            return
        }

        // 모달 열린 상태에서 ESC로 닫기
        if (key === 'Escape' && keyboardStore.isShortcutsModalOpen) {
            event.preventDefault()
            keyboardStore.closeShortcutsModal()
            return
        }

        if (keyboardStore.isShortcutsModalOpen) return

        // 입력 필드에서는 대부분의 단축키 비활성화
        // 예외: Ctrl+Enter, Esc
        if (isInputFocused()) {
            if (key === 'Escape' || (key === 'Enter' && ctrlKey)) {
                // 페이지별로 처리하도록 전파
                return
            }
            // 다른 단축키는 무시
            return
        }

        // Ctrl/Meta 조합
        if (ctrlKey || metaKey) {
            if (key === 'k' || key === 'K') {
                event.preventDefault()
                router.push('/search')
                return
            }
            return
        }

        // Alt 조합
        if (altKey) {
            if (key === 'n' || key === 'N') {
                // 알림 페이지 (N과 충돌하므로 Alt 사용)
                if (authStore.isAuthenticated) {
                    event.preventDefault()
                    router.push('/mypage/notifications')
                }
                return
            }
            return
        }

        // Shift 조합
        if (shiftKey) {
            if (key === 'B') {
                // 전체 게시판 목록 페이지
                event.preventDefault()
                router.push('/boards')
                return
            }
            if ((key === '/' || key === '?') && window.innerWidth >= 640) {
                event.preventDefault()
                keyboardStore.toggleShortcutsModal()
                return
            }
            return
        }

        // 단일 키
        switch (key) {
            case 's':
            case 'S':
                // 구독 게시판 드롭다운 (로그인 시)
                if (authStore.isAuthenticated) {
                    event.preventDefault()
                    handlers.toggleSubscriptionDropdown?.()
                }
                break

            case 'b':
                // 전체 게시판 드롭다운
                event.preventDefault()
                handlers.toggleAllBoardsDropdown?.()
                break

            case 'h':
                // 홈 이동
                event.preventDefault()
                router.push('/')
                break

            case 'm':
                // 마이페이지 드롭다운 (로그인 시)
                if (authStore.isAuthenticated) {
                    event.preventDefault()
                    handlers.toggleUserDropdown?.()
                }
                break

            case 'd':
                // 다크모드 토글
                event.preventDefault()
                toggleTheme()
                break

            case 'q':
                if (authStore.isAuthenticated) {
                    event.preventDefault()
                    void handlers.logout?.()
                }
                break
        }
    }

    onMounted(() => {
        document.addEventListener('keydown', handleKeyDown)
    })

    onUnmounted(() => {
        document.removeEventListener('keydown', handleKeyDown)
    })

    return {
        keyboardStore,
        isInputFocused,
    }
}

/**
 * 게시판 목록 페이지용 단축키
 */
export interface BoardDetailShortcutHandlers {
    goToNextPage: () => void
    goToPrevPage: () => void
    goToFirstPage: () => void
    goToLastPage: () => void
    goToWrite: () => void
    toggleSubscribe: () => void
    focusSearch: () => void
    canWrite: boolean | (() => boolean)
    canGoNext: boolean | (() => boolean)
    canGoPrev: boolean | (() => boolean)
    canToggleSubscribe?: boolean | (() => boolean)
    shouldIgnoreShortcut?: () => boolean
}

export function useBoardDetailShortcuts(handlers: BoardDetailShortcutHandlers) {
    const authStore = useAuthStore()

    const resolveGuard = (guard: boolean | (() => boolean) | undefined, defaultValue = false): boolean => {
        if (typeof guard === 'function') {
            return guard()
        }
        return guard ?? defaultValue
    }

    const handleKeyDown = (event: KeyboardEvent) => {
        const { key, shiftKey, ctrlKey, altKey, metaKey } = event

        if (ctrlKey || altKey || metaKey) return
        if (isInputFocused()) return
        if (handlers.shouldIgnoreShortcut?.()) return

        if (shiftKey) {
            if (key === '[' || key === '{') {
                event.preventDefault()
                handlers.goToFirstPage()
                return
            }
            if (key === ']' || key === '}') {
                event.preventDefault()
                handlers.goToLastPage()
                return
            }
            return
        }

        switch (key) {
            case ']':
                if (resolveGuard(handlers.canGoNext)) {
                    event.preventDefault()
                    handlers.goToNextPage()
                }
                break

            case '[':
                if (resolveGuard(handlers.canGoPrev)) {
                    event.preventDefault()
                    handlers.goToPrevPage()
                }
                break

            case 'n':
            case 'N':
                if (resolveGuard(handlers.canWrite)) {
                    event.preventDefault()
                    handlers.goToWrite()
                }
                break

            case 'f':
            case 'F':
                if (authStore.isAuthenticated && resolveGuard(handlers.canToggleSubscribe, true)) {
                    event.preventDefault()
                    handlers.toggleSubscribe()
                }
                break

            case '/':
                event.preventDefault()
                handlers.focusSearch()
                break
        }
    }

    onMounted(() => {
        document.addEventListener('keydown', handleKeyDown)
    })

    onUnmounted(() => {
        document.removeEventListener('keydown', handleKeyDown)
    })
}

/**
 * 게시글 상세 페이지용 단축키
 */
export interface PostDetailShortcutHandlers {
    scrollToComments: () => void
    goToList: () => void
    toggleLike: () => void
    toggleScrap: () => void
    copyUrl: () => void
    shareUrl: () => void
    goToEdit: () => void
    isAuthor: boolean
}

export function usePostDetailShortcuts(handlers: PostDetailShortcutHandlers) {
    const authStore = useAuthStore()

    const handleKeyDown = (event: KeyboardEvent) => {
        const { key, shiftKey, ctrlKey, altKey, metaKey } = event

        if (ctrlKey || altKey || metaKey) return
        if (isInputFocused()) return

        if (shiftKey) {
            if (key === 'S') {
                // 스크랩 (S와 충돌하므로 Shift 사용)
                if (authStore.isAuthenticated) {
                    event.preventDefault()
                    handlers.toggleScrap()
                }
                return
            }
            if (key === 'Y') {
                // 공유
                event.preventDefault()
                handlers.shareUrl()
                return
            }
            return
        }

        switch (key) {
            case 'c':
                // 댓글 영역으로 이동
                event.preventDefault()
                handlers.scrollToComments()
                break

            case 'u':
                // 목록으로 이동
                event.preventDefault()
                handlers.goToList()
                break

            case 'l':
                // 좋아요
                if (authStore.isAuthenticated) {
                    event.preventDefault()
                    handlers.toggleLike()
                }
                break

            case 'y':
                // URL 복사
                event.preventDefault()
                handlers.copyUrl()
                break

            case 'e':
                // 수정 (작성자만)
                if (handlers.isAuthor) {
                    event.preventDefault()
                    handlers.goToEdit()
                }
                break

            case 'Escape':
                // 목록으로 이동
                event.preventDefault()
                handlers.goToList()
                break
        }
    }

    onMounted(() => {
        document.addEventListener('keydown', handleKeyDown)
    })

    onUnmounted(() => {
        document.removeEventListener('keydown', handleKeyDown)
    })
}

/**
 * 글 작성/수정 페이지용 단축키
 */
export interface PostWriteShortcutHandlers {
    submit: () => void
    cancel: () => Promise<void>
}

export function usePostWriteShortcuts(handlers: PostWriteShortcutHandlers) {
    const handleKeyDown = async (event: KeyboardEvent) => {
        const { key, ctrlKey, metaKey } = event

        // Ctrl+Enter: 제출
        if ((ctrlKey || metaKey) && key === 'Enter') {
            event.preventDefault()
            handlers.submit()
            return
        }

        // Esc: 취소 (confirm 후)
        if (key === 'Escape') {
            event.preventDefault()
            await handlers.cancel()
            return
        }
    }

    onMounted(() => {
        document.addEventListener('keydown', handleKeyDown)
    })

    onUnmounted(() => {
        document.removeEventListener('keydown', handleKeyDown)
    })
}

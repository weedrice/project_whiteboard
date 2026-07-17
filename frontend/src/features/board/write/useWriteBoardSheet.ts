import { useQueryClient } from '@tanstack/vue-query'
import { computed, nextTick, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useBoard } from '@/features/board/useBoard'
import { useAuthGuard } from '@/composables/useAuthGuard'
import { useBodyScrollLock } from '@/composables/useBodyScrollLock'
import { useToastStore } from '@/stores/toast'
import {
  BOARD_WRITE_FORBIDDEN_MESSAGE_KEY,
  BOARD_WRITE_VERIFY_FAILED_MESSAGE_KEY,
  canUserWriteBoardPost,
  fetchBoardForWriteAccess,
} from '@/features/board/access/useBoardWriteAccess'
import i18n from '@/i18n'
import { encodePathSegment } from '@/utils/urlPath'

export function useWriteBoardSheet() {
  const route = useRoute()
  const router = useRouter()
  const queryClient = useQueryClient()
  const authStore = useAuthStore()
  const { requireAuth } = useAuthGuard()
  const toastStore = useToastStore()
  const { useBoards, useSubscribedBoards } = useBoard()

  const showWriteSheet = ref(false)
  const fabButtonRef = ref<HTMLButtonElement | null>(null)
  const sheetRef = ref<HTMLElement | null>(null)
  const lastFocusedElement = ref<HTMLElement | null>(null)
  const shouldFetchSubscriptions = computed(() => authStore.isAuthenticated && showWriteSheet.value)
  useBodyScrollLock(showWriteSheet)

  const { data: boards, isError: isBoardsError, refetch: refetchBoards } = useBoards()
  const {
    data: subscribedBoards,
    isLoading: isSubscribedBoardsLoading,
    isError: isSubscribedBoardsError,
    refetch: refetchSubscribedBoards,
  } = useSubscribedBoards(8, shouldFetchSubscriptions)

  const publicBoards = computed(() => boards.value?.slice(0, 8) ?? [])
  const preferredBoards = computed(() => {
    if (subscribedBoards.value?.length) {
      return subscribedBoards.value
    }

    return publicBoards.value
  })

  const closeWriteSheet = () => {
    showWriteSheet.value = false
  }

  const retryBoardOptions = () => {
    void Promise.all([
      refetchBoards(),
      authStore.isAuthenticated ? refetchSubscribedBoards() : Promise.resolve(),
    ])
  }

  const openWriteSheet = async () => {
    if (!requireAuth(route.fullPath)) {
      return
    }

    lastFocusedElement.value = document.activeElement instanceof HTMLElement ? document.activeElement : null
    showWriteSheet.value = true
  }

  const verifyBoardWriteAccess = async (boardUrl: string) => {
    const board = await fetchBoardForWriteAccess(
      queryClient,
      boardUrl,
      authStore.sessionGeneration,
    )
    return canUserWriteBoardPost(board, authStore.isAuthenticated, authStore.user?.role)
  }

  const goToBoardWrite = async (boardUrl: string) => {
    try {
      const canWrite = await verifyBoardWriteAccess(boardUrl)
      if (!canWrite) {
        toastStore.addToast(i18n.global.t(BOARD_WRITE_FORBIDDEN_MESSAGE_KEY), 'error')
        return
      }

      showWriteSheet.value = false
      await router.push(`/board/${encodePathSegment(boardUrl)}/write`)
    } catch {
      toastStore.addToast(i18n.global.t(BOARD_WRITE_VERIFY_FAILED_MESSAGE_KEY), 'error')
    }
  }

  const handleSheetKeydown = (event: KeyboardEvent) => {
    if (event.key === 'Escape') {
      event.preventDefault()
      showWriteSheet.value = false
      return
    }

    if (event.key !== 'Tab' || !sheetRef.value) return

    const focusable = Array.from(
      sheetRef.value.querySelectorAll<HTMLElement>(
        'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'
      )
    ).filter((element) => !element.hasAttribute('disabled') && !element.getAttribute('aria-hidden'))

    if (focusable.length === 0) return

    const first = focusable[0]
    const last = focusable[focusable.length - 1]
    const active = document.activeElement as HTMLElement | null

    if (active === sheetRef.value) {
      event.preventDefault()
      ;(event.shiftKey ? last : first).focus()
      return
    }

    if (event.shiftKey && active === first) {
      event.preventDefault()
      last.focus()
      return
    }

    if (!event.shiftKey && active === last) {
      event.preventDefault()
      first.focus()
    }
  }

  watch(
    () => route.fullPath,
    () => {
      showWriteSheet.value = false
    }
  )

  watch(showWriteSheet, async (isOpen) => {
    if (isOpen) {
      await nextTick()
      sheetRef.value?.focus()
      return
    }

    const restoreTarget = lastFocusedElement.value ?? fabButtonRef.value
    restoreTarget?.focus()
  })

  return {
    fabButtonRef,
    sheetRef,
    showWriteSheet,
    preferredBoards,
    isSubscribedBoardsLoading,
    isBoardsError,
    isSubscribedBoardsError,
    openWriteSheet,
    closeWriteSheet,
    goToBoardWrite,
    handleSheetKeydown,
    retryBoardOptions,
  }
}

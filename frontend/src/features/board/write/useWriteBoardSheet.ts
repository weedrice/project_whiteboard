import { useQueryClient } from '@tanstack/vue-query'
import { computed, getCurrentScope, onScopeDispose, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useBoard } from '@/features/board/useBoard'
import { useAuthGuard } from '@/composables/useAuthGuard'
import { useDialogLifecycle } from '@/composables/useDialogLifecycle'
import { useToastStore } from '@/stores/toast'
import {
  BOARD_WRITE_FORBIDDEN_MESSAGE_KEY,
  BOARD_WRITE_VERIFY_FAILED_MESSAGE_KEY,
  canUserWriteBoardPost,
  fetchBoardForWriteAccess,
} from '@/features/board/access/useBoardWriteAccess'
import i18n from '@/i18n'
import { encodePathSegment } from '@/utils/urlPath'
import { subscribeAuthSessionBoundary } from '@/queryAuthScope'

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
  const sheetOverlayRef = ref<HTMLElement | null>(null)
  const isVerifyingWriteAccess = ref(false)
  let verificationRevision = 0
  let verificationController: AbortController | null = null
  const shouldFetchSubscriptions = computed(() => authStore.isAuthenticated && showWriteSheet.value)

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

  const cancelVerification = () => {
    verificationRevision += 1
    verificationController?.abort()
    verificationController = null
    isVerifyingWriteAccess.value = false
  }

  const closeWriteSheet = () => {
    cancelVerification()
    showWriteSheet.value = false
  }

  const { isTopDialog } = useDialogLifecycle({
    isOpen: showWriteSheet,
    dialogRef: sheetRef,
    overlayRef: sheetOverlayRef,
    close: closeWriteSheet,
    initialFocus: 'container',
  })

  const stopSessionBoundary = subscribeAuthSessionBoundary(() => closeWriteSheet())
  if (getCurrentScope()) onScopeDispose(stopSessionBoundary)

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

    showWriteSheet.value = true
  }

  const verifyBoardWriteAccess = async (boardUrl: string, generation: number, signal: AbortSignal) => {
    const board = await fetchBoardForWriteAccess(
      queryClient,
      boardUrl,
      generation,
      signal,
    )
    return canUserWriteBoardPost(board, authStore.isAuthenticated, authStore.user?.role)
  }

  const goToBoardWrite = async (boardUrl: string) => {
    if (!showWriteSheet.value) return
    cancelVerification()
    const controller = new AbortController()
    verificationController = controller
    const revision = verificationRevision
    const generation = authStore.sessionGeneration
    isVerifyingWriteAccess.value = true
    const isCurrent = () => verificationController === controller
      && verificationRevision === revision
      && !controller.signal.aborted
      && showWriteSheet.value
      && authStore.sessionGeneration === generation
    try {
      const canWrite = await verifyBoardWriteAccess(boardUrl, generation, controller.signal)
      if (!isCurrent()) return
      if (!canWrite) {
        toastStore.addToast(i18n.global.t(BOARD_WRITE_FORBIDDEN_MESSAGE_KEY), 'error')
        return
      }

      closeWriteSheet()
      await router.push(`/board/${encodePathSegment(boardUrl)}/write`)
    } catch {
      if (!isCurrent()) return
      toastStore.addToast(i18n.global.t(BOARD_WRITE_VERIFY_FAILED_MESSAGE_KEY), 'error')
    } finally {
      if (verificationController === controller) {
        verificationController = null
        isVerifyingWriteAccess.value = false
      }
    }
  }

  watch(
    () => route.fullPath,
    () => {
      closeWriteSheet()
    }
  )

  return {
    fabButtonRef,
    sheetRef,
    sheetOverlayRef,
    isTopDialog,
    showWriteSheet,
    preferredBoards,
    isSubscribedBoardsLoading,
    isBoardsError,
    isSubscribedBoardsError,
    isVerifyingWriteAccess,
    openWriteSheet,
    closeWriteSheet,
    goToBoardWrite,
    retryBoardOptions,
  }
}

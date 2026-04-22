import { computed, nextTick, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useBoard } from '@/composables/useBoard'
import { useToastStore } from '@/stores/toast'
import { boardApi } from '@/api/board'
import type { BoardDetail, Category } from '@/types'

export function useWriteBoardSheet() {
  const route = useRoute()
  const router = useRouter()
  const authStore = useAuthStore()
  const toastStore = useToastStore()
  const { useBoards, useSubscribedBoards } = useBoard()

  const showWriteSheet = ref(false)
  const fabButtonRef = ref<HTMLButtonElement | null>(null)
  const sheetRef = ref<HTMLElement | null>(null)
  const lastFocusedElement = ref<HTMLElement | null>(null)
  const shouldFetchSubscriptions = computed(() => authStore.isAuthenticated && showWriteSheet.value)

  const { data: boards, isError: isBoardsError } = useBoards()
  const {
    data: subscribedBoards,
    isLoading: isSubscribedBoardsLoading,
    isError: isSubscribedBoardsError,
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

  const openWriteSheet = async () => {
    if (!authStore.isAuthenticated) {
      await router.push({ name: 'login', query: { redirect: route.fullPath } })
      return
    }

    lastFocusedElement.value = document.activeElement instanceof HTMLElement ? document.activeElement : null
    showWriteSheet.value = true
  }

  const canWriteCategory = (category: Category, board: BoardDetail) => {
    const userRole = authStore.user?.role || 'USER'
    const normalizedRole = category.minWriteRole || 'USER'

    switch (normalizedRole) {
      case 'USER':
        return true
      case 'BOARD_ADMIN':
        return userRole === 'SUPER_ADMIN' || board.isAdmin
      case 'SUPER_ADMIN':
        return userRole === 'SUPER_ADMIN'
      default:
        return false
    }
  }

  const verifyBoardWriteAccess = async (boardUrl: string) => {
    const [{ data: boardResponse }, { data: categoriesResponse }] = await Promise.all([
      boardApi.getBoard(boardUrl),
      boardApi.getCategories(boardUrl),
    ])

    const board = boardResponse.data
    const categories = categoriesResponse.data
    if (!categories.length) return true

    return categories.some((category) => canWriteCategory(category, board))
  }

  const goToBoardWrite = async (boardUrl: string) => {
    try {
      const canWrite = await verifyBoardWriteAccess(boardUrl)
      if (!canWrite) {
        toastStore.addToast('You do not have permission to write on this board.', 'error')
        return
      }

      showWriteSheet.value = false
      await router.push(`/board/${boardUrl}/write`)
    } catch {
      toastStore.addToast('Unable to verify write access for this board.', 'error')
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
  }
}

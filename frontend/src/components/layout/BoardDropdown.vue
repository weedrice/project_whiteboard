<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ChevronDown, List } from 'lucide-vue-next'
import { useAuthStore } from '@/stores/auth'
import { useBoard } from '@/composables/useBoard'
import { useKeyboardStore, type DropdownItem } from '@/stores/keyboard'
import BaseButton from '@/components/common/ui/BaseButton.vue'

const props = defineProps<{
  type: 'subscription' | 'all'
  isOpen: boolean
  desktopLabel?: string
  mobileLabel?: string
}>()

const emit = defineEmits<{
  (e: 'toggle'): void
}>()

const router = useRouter()
const authStore = useAuthStore()
const keyboardStore = useKeyboardStore()
const dropdownRef = ref<HTMLElement | null>(null)
const { useBoards, useSubscribedBoards } = useBoard()

// Computed to determine if subscriptions query should be enabled
const shouldFetchSubscriptions = computed(() => {
  return props.type === 'subscription' && props.isOpen && authStore.isAuthenticated
})

// Use TanStack Query for data fetching
const { data: allBoards, isLoading: loadingAll, isError: allBoardsError } = useBoards()
const {
  data: subscribedBoards,
  isLoading: loadingSubscriptions,
  isError: subscriptionsError,
} = useSubscribedBoards(10, shouldFetchSubscriptions)

// Computed values for items and loading state
const items = computed(() => {
  if (props.type === 'subscription') {
    return subscribedBoards.value || []
  } else {
    return allBoards.value || []
  }
})

const loading = computed(() => {
  return props.type === 'subscription' ? loadingSubscriptions.value : loadingAll.value
})

const isError = computed(() => {
  return props.type === 'subscription' ? subscriptionsError.value : allBoardsError.value
})

// 최대 10개까지 숫자 배지 표시
const displayItems = computed(() => {
  return items.value.slice(0, 10)
})

// 숫자 배지 표시 (1-9, 0)
const getNumberBadge = (index: number): string => {
  if (index < 9) return String(index + 1)
  if (index === 9) return '0'
  return ''
}

const toggleDropdown = () => {
  emit('toggle')
}

const handleMoreClick = () => {
  emit('toggle') // Close dropdown
  router.push('/boards')
}

const navigateToBoard = (boardUrl: string) => {
  emit('toggle')
  router.push(`/board/${boardUrl}`)
}

// 드롭다운 열릴 때 keyboard store에 항목 등록
watch(() => props.isOpen, (isOpen) => {
  if (isOpen) {
    const dropdownItems: DropdownItem[] = displayItems.value.map((board) => ({
      label: board.boardName,
      action: () => navigateToBoard(board.boardUrl),
    }))
    keyboardStore.setOpenDropdown(props.type, dropdownItems)
  }
}, { immediate: true })

// 키보드 이벤트 핸들러
const handleKeyDown = (event: KeyboardEvent) => {
  if (!props.isOpen) return

  // ESC로 닫기
  if (event.key === 'Escape') {
    event.preventDefault()
    emit('toggle')
    keyboardStore.closeDropdown()
    return
  }

  // 숫자키로 선택
  if (event.key >= '0' && event.key <= '9') {
    let index = -1
    if (event.key >= '1' && event.key <= '9') {
      index = parseInt(event.key) - 1
    } else if (event.key === '0') {
      index = 9
    }

    if (index >= 0 && index < displayItems.value.length) {
      event.preventDefault()
      navigateToBoard(displayItems.value[index].boardUrl)
    }
  }
}

onMounted(() => {
  document.addEventListener('keydown', handleKeyDown)
})

onUnmounted(() => {
  document.removeEventListener('keydown', handleKeyDown)
})
</script>

<template>
  <div class="relative" ref="dropdownRef">
    <BaseButton @click.stop="toggleDropdown" variant="ghost"
      :aria-expanded="isOpen"
      aria-haspopup="menu"
      class="nv-shell-tab nv-shell-tab-button flex items-center justify-center sm:justify-start space-x-1 px-3 py-2 rounded-full text-xs sm:text-sm font-medium focus:outline-none whitespace-nowrap min-w-0 min-h-[40px] sm:min-h-0 touch-manipulation">
      <span v-if="type === 'subscription'" class="sm:hidden">{{ mobileLabel || $t('board.list.subscribedShort') }}</span>
      <span v-if="type === 'subscription'" class="hidden sm:inline">{{ desktopLabel || $t('board.list.subscribed') }}</span>
      <span v-if="type === 'all'" class="sm:hidden">{{ mobileLabel || $t('board.list.allShort') }}</span>
      <span v-if="type === 'all'" class="hidden sm:inline">{{ desktopLabel || $t('board.list.all') }}</span>
      <ChevronDown class="hidden sm:inline-block h-3 w-3 sm:h-4 sm:w-4 flex-shrink-0" />
    </BaseButton>

    <div v-if="isOpen"
      role="menu"
      class="origin-top-left absolute left-0 mt-2 w-[min(16rem,92vw)] sm:w-64 rounded-md shadow-lg py-1 sm:py-1 bg-white dark:bg-gray-800 ring-1 ring-black ring-opacity-5 dark:ring-gray-700 focus:outline-none z-50">
      <div v-if="loading" class="px-3 py-3 sm:py-3 text-center">
        <div class="animate-spin rounded-full h-5 w-5 border-b-2 border-indigo-600 mx-auto"></div>
      </div>

      <div v-else-if="isError" class="px-3 py-3 sm:py-3 text-center text-xs sm:text-sm text-red-500 dark:text-red-400">
        <span>Unable to load boards.</span>
      </div>

      <div v-else-if="items.length > 0">
        <div class="max-h-96 overflow-y-auto">
          <button v-for="(board, index) in displayItems" :key="board.boardUrl" role="menuitem" @click="navigateToBoard(board.boardUrl)"
            class="group flex items-center justify-between w-full px-3 py-2.5 sm:py-2 min-h-[40px] sm:min-h-0 text-xs sm:text-sm text-gray-700 dark:text-gray-200 hover:bg-gray-50 dark:hover:bg-gray-700 active:bg-gray-100 dark:active:bg-gray-600 hover:text-gray-900 dark:hover:text-white touch-manipulation">
            <span class="truncate">{{ board.boardName }}</span>
            <kbd v-if="index < 10"
              class="hidden sm:inline-block ml-2 px-1.5 py-0.5 text-xs font-medium text-gray-500 dark:text-gray-400 bg-gray-100 dark:bg-gray-600 border border-gray-200 dark:border-gray-500 rounded">
              {{ getNumberBadge(index) }}
            </kbd>
          </button>
        </div>

        <div v-if="type === 'all'" class="border-t border-gray-100 dark:border-gray-700 pt-1">
          <BaseButton @click="handleMoreClick" variant="ghost" full-width role="menuitem"
            class="w-full text-left group flex items-center px-3 py-2.5 sm:py-2 min-h-[40px] sm:min-h-0 text-xs sm:text-sm text-indigo-600 dark:text-indigo-400 hover:bg-indigo-50 dark:hover:bg-indigo-900/50 active:bg-indigo-100 dark:active:bg-indigo-900/70 font-medium justify-start touch-manipulation">
            <List class="mr-2 h-3 w-3 sm:h-4 sm:w-4 flex-shrink-0" />
            {{ $t('common.loadMore') }}
          </BaseButton>
        </div>
      </div>

      <div v-else class="px-3 py-3 sm:py-3 text-xs sm:text-sm text-gray-500 dark:text-gray-400 text-center">
        <span v-if="type === 'subscription'">{{ $t('board.list.noSubscribed') }}</span>
        <span v-else>{{ $t('board.list.noBoards') }}</span>
      </div>
    </div>
  </div>
</template>

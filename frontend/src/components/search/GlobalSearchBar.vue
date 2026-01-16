<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted, computed, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { useBoard } from '@/composables/useBoard'
import { Search } from 'lucide-vue-next'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import { useDebounce } from '@/composables/useDebounce'
import { useKeyboardNavigation } from '@/composables/useKeyboardNavigation'
import { getOptimizedBoardIconUrl, handleImageError } from '@/utils/image'
import { DEBOUNCE_DELAY } from '@/utils/constants'
import type { Board } from '@/types'

const router = useRouter()
const { useBoards } = useBoard()
const { data: boardsData } = useBoards()

const searchQuery = ref('')
const debouncedSearchQuery = useDebounce(searchQuery, DEBOUNCE_DELAY.SEARCH)
const showDropdown = ref(false)
const searchContainer = ref<HTMLElement | null>(null)
const searchInputRef = ref<HTMLInputElement | null>(null)

const boards = computed(() => boardsData.value || [])
const filteredBoards = computed(() => {
  if (!debouncedSearchQuery.value.trim()) return []
  const query = debouncedSearchQuery.value.toLowerCase()
  return boards.value.filter((board: Board) =>
    board.boardName.toLowerCase().includes(query)
  )
})

// 키보드 네비게이션
const { selectedIndex, handleKeyDown: handleDropdownKeyDown, reset: resetSelection, setSelectedIndex } = useKeyboardNavigation(
  filteredBoards,
  {
    onSelect: (index) => {
      if (filteredBoards.value[index]) {
        selectBoard(filteredBoards.value[index].boardUrl)
      }
    },
    onEscape: () => {
      showDropdown.value = false
      searchInputRef.value?.focus()
    },
    loop: true,
    initialIndex: -1
  }
)

// Handle search submission (Full Search)
const handleSearch = () => {
  if (searchQuery.value.trim()) {
    showDropdown.value = false
    router.push({
      name: 'search',
      query: {
        q: searchQuery.value,
        t: Date.now().toString() // Force refresh by adding timestamp
      }
    })
  }
}

// Handle board selection (Autocomplete)
const selectBoard = (boardUrl: string) => {
  showDropdown.value = false
  searchQuery.value = '' // Optional: clear search after navigation
  router.push(`/board/${boardUrl}`)
}

// Watch for input changes (use debounced query for filtering, but show dropdown immediately)
watch(searchQuery, () => {
  showDropdown.value = !!searchQuery.value.trim()
  resetSelection()
})

// Watch for filtered boards changes to reset selection
watch(filteredBoards, () => {
  resetSelection()
})

// Click outside to close dropdown
const handleClickOutside = (event: Event) => {
  if (searchContainer.value && !searchContainer.value.contains(event.target as Node)) {
    showDropdown.value = false
    resetSelection()
  }
}

// Input 키보드 이벤트 핸들러
const handleInputKeyDown = (event: KeyboardEvent) => {
  if (showDropdown.value && filteredBoards.value.length > 0) {
    // If Enter is pressed and no item is selected, perform full search
    if (event.key === 'Enter' && selectedIndex.value === -1) {
      handleSearch()
      return
    }
    // 드롭다운이 열려있으면 드롭다운 네비게이션 사용
    handleDropdownKeyDown(event)
  } else if (event.key === 'Enter') {
    // 드롭다운이 없으면 검색 실행
    handleSearch()
  }
}

onMounted(() => {
  document.addEventListener('click', handleClickOutside)
})

onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside)
})
</script>

<template>
  <div ref="searchContainer" class="relative w-full max-w-md">
    <div class="relative">
      <BaseInput ref="searchInputRef" v-model="searchQuery" @keydown="handleInputKeyDown"
        @focus="showDropdown = !!searchQuery.trim()" :placeholder="$t('search.placeholder')"
        inputClass="w-64 rounded-full pl-10 pr-4 py-2 border-gray-300 focus:ring-indigo-500 focus:border-transparent dark:bg-gray-700 dark:border-gray-600 dark:text-white dark:placeholder-gray-400"
        hideLabel>
        <template #prefix>
          <Search class="h-4 w-4 text-gray-400 dark:text-gray-500" />
        </template>
      </BaseInput>
    </div>

    <!-- Autocomplete Dropdown -->
    <div v-if="showDropdown"
      class="absolute z-50 mt-1 w-full bg-white dark:bg-gray-800 rounded-md shadow-lg border border-gray-200 dark:border-gray-700 max-h-96 overflow-y-auto">
      <!-- Board Results -->
      <div v-if="filteredBoards.length > 0">
        <div class="px-3 py-2 text-xs font-semibold text-gray-500 dark:text-gray-400 bg-gray-50 dark:bg-gray-700">
          {{ $t('search.boards') }}
        </div>
        <ul role="listbox" aria-label="Board search results">
          <li v-for="(board, index) in filteredBoards" :key="board.boardUrl" @click="selectBoard(board.boardUrl)"
            @mouseenter="setSelectedIndex(index)" :class="[
              'px-4 py-2 cursor-pointer flex items-center space-x-3',
              index === selectedIndex
                ? 'bg-indigo-50 dark:bg-indigo-900/20'
                : 'hover:bg-gray-100 dark:hover:bg-gray-700'
            ]" :aria-selected="index === selectedIndex" role="option" tabindex="-1">
            <!-- Board Icon/Thumbnail -->
            <div
              class="flex-shrink-0 h-8 w-8 rounded bg-indigo-100 flex items-center justify-center text-indigo-600 font-bold overflow-hidden border border-gray-200">
              <img v-if="board.iconUrl" :src="getOptimizedBoardIconUrl(board.iconUrl)"
                class="h-full w-full object-contain bg-white" alt="" @error="handleImageError($event)" />
              <span v-else class="text-xs">{{ board.boardName.substring(0, 1) }}</span>
            </div>
            <span class="text-sm text-gray-900 dark:text-white font-medium">{{ board.boardName }}</span>
          </li>
        </ul>
      </div>

      <!-- Full Search Option -->
      <div @click="handleSearch"
        class="border-t border-gray-200 dark:border-gray-700 px-4 py-3 hover:bg-gray-100 dark:hover:bg-gray-700 cursor-pointer flex items-center text-indigo-600 dark:text-indigo-400">
        <Search class="h-4 w-4 mr-2" />
        <span class="text-sm font-medium">{{ $t('search.doSearch', { query: searchQuery }) }}</span>
      </div>
    </div>
  </div>
</template>

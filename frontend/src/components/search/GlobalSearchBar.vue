<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted, computed, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { useBoard } from '@/composables/useBoard'
import { Search, X } from 'lucide-vue-next'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import { useDebounce } from '@/composables/useDebounce'
import { useKeyboardNavigation } from '@/composables/useKeyboardNavigation'
import { getOptimizedBoardIconUrl, handleImageError } from '@/utils/image'
import { DEBOUNCE_DELAY } from '@/utils/constants'
import type { BoardListItem } from '@/types'

const router = useRouter()
const { useBoards } = useBoard()
const { data: boardsData } = useBoards()

const searchQuery = ref('')
const debouncedSearchQuery = useDebounce(searchQuery, DEBOUNCE_DELAY.SEARCH)
const showDropdown = ref(false)
const searchContainer = ref<HTMLElement | null>(null)
const searchInputRef = ref<HTMLInputElement | null>(null)

// 모바일(640px 미만): 돋보기만 보이다가 클릭 시 검색 바 확장
const isMobile = ref(typeof window !== 'undefined' && window.innerWidth < 640)
const isExpanded = ref(false)
const mediaQuery = typeof window !== 'undefined' ? window.matchMedia('(max-width: 639px)') : null
const updateIsMobile = () => {
  if (mediaQuery) {
    isMobile.value = mediaQuery.matches
    if (!mediaQuery.matches) isExpanded.value = false
  }
}
const focusSearchInput = () => {
  const comp = searchInputRef.value as { $el?: HTMLElement } | null
  const el = comp?.$el
  const input = el?.querySelector?.('input')
  if (input instanceof HTMLInputElement) input.focus()
}

const expandAndFocus = () => {
  isExpanded.value = true
  // BaseInput이 v-else로 마운트된 뒤 ref가 채워지도록 nextTick + rAF
  nextTick(() => {
    nextTick(() => {
      requestAnimationFrame(() => focusSearchInput())
    })
  })
}
const collapse = () => {
  isExpanded.value = false
  showDropdown.value = false
  if (document.activeElement instanceof HTMLElement) document.activeElement.blur()
}

const boards = computed(() => boardsData.value || [])
const filteredBoards = computed(() => {
  if (!debouncedSearchQuery.value.trim()) return []
  const query = debouncedSearchQuery.value.toLowerCase()
  return boards.value.filter((board: BoardListItem) =>
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
    if (isMobile.value) collapse()
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
  searchQuery.value = ''
  if (isMobile.value) collapse()
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

// Click outside to close dropdown (모바일 확장 시 접기)
const handleClickOutside = (event: Event) => {
  if (!searchContainer.value || searchContainer.value.contains(event.target as Node)) return
  // 모바일 확장 중 입력 포커스가 있으면 접지 않음 (키보드 열림/레이아웃 변경 시 오탐 방지)
  if (isMobile.value && isExpanded.value && document.activeElement && searchContainer.value.contains(document.activeElement)) return
  showDropdown.value = false
  resetSelection()
  if (isMobile.value && isExpanded.value) collapse()
}

// Input 키보드 이벤트 핸들러
const handleInputKeyDown = (event: KeyboardEvent) => {
  // ESC: 포커스 해제 (모바일 확장 시 검색 바 접기)
  if (event.key === 'Escape') {
    event.preventDefault()
    if (isMobile.value && isExpanded.value) {
      collapse()
      return
    }
    showDropdown.value = false
    resetSelection()
    if (document.activeElement instanceof HTMLElement) document.activeElement.blur()
    return
  }

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
  if (mediaQuery) {
    isMobile.value = mediaQuery.matches
    mediaQuery.addEventListener('change', updateIsMobile)
  }
  document.addEventListener('click', handleClickOutside)
})

onUnmounted(() => {
  if (mediaQuery) mediaQuery.removeEventListener('change', updateIsMobile)
  document.removeEventListener('click', handleClickOutside)
})
</script>

<template>
  <div
    ref="searchContainer"
    class="flex items-center transition-[width] duration-200 ease-out"
    :class="
      isMobile && !isExpanded
        ? 'relative w-10 flex-shrink-0'
        : isMobile && isExpanded
          ? 'fixed inset-x-0 top-0 z-[100] h-16 px-4 bg-white dark:bg-gray-800 shadow-sm border-b border-gray-200 dark:border-gray-700'
          : 'relative w-full max-w-[5rem] sm:max-w-xs md:max-w-md'
    ">
    <!-- 모바일: 접혀 있을 때 돋보기만 -->
    <button
      v-if="isMobile && !isExpanded"
      type="button"
      @click.stop="expandAndFocus"
      class="flex items-center justify-center w-10 h-10 rounded-full text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-200 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2 dark:focus:ring-offset-gray-800"
      :aria-label="$t('search.placeholder')">
      <Search class="h-5 w-5" />
    </button>
    <!-- 모바일 확장 시: 상단 전체 검색 바 | 데스크톱: 검색 바 -->
    <div v-else class="relative flex items-center gap-2 w-full min-w-0 flex-1">
      <div class="relative flex-1 min-w-0">
        <BaseInput
          ref="searchInputRef"
          v-model="searchQuery"
          @keydown="handleInputKeyDown"
          @focus="showDropdown = !!searchQuery.trim()"
          :placeholder="$t('search.placeholder')"
          inputClass="w-full min-w-0 rounded-full pl-9 pr-4 py-2 border-gray-300 focus:ring-indigo-500 focus:border-transparent dark:bg-gray-700 dark:border-gray-600 dark:text-white dark:placeholder-gray-400 text-sm"
          hideLabel>
          <template #prefix>
            <Search class="h-4 w-4 text-gray-400 dark:text-gray-500" />
          </template>
        </BaseInput>
      </div>
      <!-- 모바일 확장 시 접기 버튼 -->
      <button
        v-if="isMobile && isExpanded"
        type="button"
        @click="collapse"
        class="flex-shrink-0 p-2 rounded-full text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-200 focus:outline-none"
        :aria-label="$t('common.cancel')">
        <X class="h-5 w-5" />
      </button>
    </div>

    <!-- Autocomplete Dropdown: 모바일 확장 시에만 body에 고정 배치(검색 바 밀림 방지) -->
    <Teleport to="body">
      <div
        v-if="showDropdown && isMobile && isExpanded"
        class="fixed inset-x-0 top-16 z-[99] mx-0 rounded-t-none border-t-0 bg-white dark:bg-gray-800 shadow-lg border border-gray-200 dark:border-gray-700 border-t-0 max-h-96 overflow-y-auto">
        <div v-if="filteredBoards.length > 0">
          <div class="px-3 py-2 text-xs font-semibold text-gray-500 dark:text-gray-400 bg-gray-50 dark:bg-gray-700">
            {{ $t('search.boards') }}
          </div>
          <ul role="listbox" aria-label="Board search results">
            <li v-for="(board, index) in filteredBoards" :key="board.boardUrl" @click="selectBoard(board.boardUrl)"
              @mouseenter="setSelectedIndex(index)" :class="[
                'px-4 py-2 cursor-pointer flex items-center space-x-3',
                index === selectedIndex ? 'bg-indigo-50 dark:bg-indigo-900/20' : 'hover:bg-gray-100 dark:hover:bg-gray-700'
              ]" :aria-selected="index === selectedIndex" role="option" tabindex="-1">
              <div class="flex-shrink-0 h-8 w-8 rounded bg-indigo-100 flex items-center justify-center text-indigo-600 font-bold overflow-hidden border border-gray-200">
                <img v-if="board.iconUrl" :src="getOptimizedBoardIconUrl(board.iconUrl)" class="h-full w-full object-contain bg-white" alt="" @error="handleImageError($event)" />
                <span v-else class="text-xs">{{ board.boardName.substring(0, 1) }}</span>
              </div>
              <span class="text-sm text-gray-900 dark:text-white font-medium">{{ board.boardName }}</span>
            </li>
          </ul>
        </div>
        <div @click="handleSearch" class="border-t border-gray-200 dark:border-gray-700 px-4 py-3 hover:bg-gray-100 dark:hover:bg-gray-700 cursor-pointer flex items-center text-indigo-600 dark:text-indigo-400">
          <Search class="h-4 w-4 mr-2" />
          <span class="text-sm font-medium">{{ $t('search.doSearch', { query: searchQuery }) }}</span>
        </div>
      </div>
    </Teleport>

    <!-- 데스크톱/접힌 모바일: 컨테이너 안에서 absolute -->
    <div
      v-if="showDropdown && (!isMobile || !isExpanded)"
      class="absolute top-full left-0 right-0 z-50 mt-1 w-full min-w-0 sm:min-w-[16rem] bg-white dark:bg-gray-800 rounded-md shadow-lg border border-gray-200 dark:border-gray-700 max-h-96 overflow-y-auto">
      <div v-if="filteredBoards.length > 0">
        <div class="px-3 py-2 text-xs font-semibold text-gray-500 dark:text-gray-400 bg-gray-50 dark:bg-gray-700">
          {{ $t('search.boards') }}
        </div>
        <ul role="listbox" aria-label="Board search results">
          <li v-for="(board, index) in filteredBoards" :key="board.boardUrl" @click="selectBoard(board.boardUrl)"
            @mouseenter="setSelectedIndex(index)" :class="[
              'px-4 py-2 cursor-pointer flex items-center space-x-3',
              index === selectedIndex ? 'bg-indigo-50 dark:bg-indigo-900/20' : 'hover:bg-gray-100 dark:hover:bg-gray-700'
            ]" :aria-selected="index === selectedIndex" role="option" tabindex="-1">
            <div class="flex-shrink-0 h-8 w-8 rounded bg-indigo-100 flex items-center justify-center text-indigo-600 font-bold overflow-hidden border border-gray-200">
              <img v-if="board.iconUrl" :src="getOptimizedBoardIconUrl(board.iconUrl)" class="h-full w-full object-contain bg-white" alt="" @error="handleImageError($event)" />
              <span v-else class="text-xs">{{ board.boardName.substring(0, 1) }}</span>
            </div>
            <span class="text-sm text-gray-900 dark:text-white font-medium">{{ board.boardName }}</span>
          </li>
        </ul>
      </div>
      <div @click="handleSearch" class="border-t border-gray-200 dark:border-gray-700 px-4 py-3 hover:bg-gray-100 dark:hover:bg-gray-700 cursor-pointer flex items-center text-indigo-600 dark:text-indigo-400">
        <Search class="h-4 w-4 mr-2" />
        <span class="text-sm font-medium">{{ $t('search.doSearch', { query: searchQuery }) }}</span>
      </div>
    </div>
  </div>
</template>

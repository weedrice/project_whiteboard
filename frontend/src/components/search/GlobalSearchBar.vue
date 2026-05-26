<script setup lang="ts">
import { ref } from 'vue'
import { Search, X } from 'lucide-vue-next'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import { useGlobalBoardSearch } from '@/composables/useGlobalBoardSearch'
import { useGlobalSearchDropdown } from '@/composables/useGlobalSearchDropdown'
import { getOptimizedBoardIconUrl, handleImageError } from '@/utils/image'

const searchContainer = ref<HTMLElement | null>(null)
const searchInputRef = ref<HTMLInputElement | null>(null)
const searchInputId = 'global-search-input'
const searchListboxId = 'global-search-board-results'

const closeBoardDropdown = () => {
  showDropdown.value = false
}

const {
  isMobile,
  isExpanded,
  focusSearchInput,
  expandAndFocus,
  collapse,
} = useGlobalSearchDropdown({
  searchContainer,
  searchInputRef,
  closeBoardDropdown,
  resetSelection: () => resetSelection(),
})

const {
  searchQuery,
  showDropdown,
  filteredBoards,
  selectedIndex,
  activeDescendantId,
  setSelectedIndex,
  handleDropdownKeyDown,
  handleSearch,
  selectBoard,
  resetSelection,
} = useGlobalBoardSearch({
  listboxId: searchListboxId,
  isMobile,
  collapse,
  focusSearchInput,
})

const handleInputKeyDown = (event: KeyboardEvent) => {
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
    if (event.key === 'Enter' && selectedIndex.value === -1) {
      handleSearch()
      return
    }
    handleDropdownKeyDown(event)
  } else if (event.key === 'Enter') {
    handleSearch()
  }
}
</script>

<template>
  <div
    ref="searchContainer"
    class="flex items-center transition-[width] duration-200 ease-out"
    :class="
      isMobile && !isExpanded
        ? 'relative w-10 flex-shrink-0'
        : isMobile && isExpanded
          ? 'nv-global-search-mobile fixed inset-x-0 top-0 z-[100] h-16 px-4 shadow-sm border-b'
          : 'relative w-full max-w-[5rem] sm:max-w-xs md:max-w-md'
    ">
    <!-- 모바일: 접혀 있을 때 돋보기만 -->
    <button
      v-if="isMobile && !isExpanded"
      type="button"
      @click.stop="expandAndFocus"
      class="nv-global-search-toggle flex items-center justify-center w-10 h-10 rounded-full focus:outline-none"
      :aria-label="$t('search.placeholder')">
      <Search class="h-5 w-5" />
    </button>
    <!-- 모바일 확장 시: 상단 전체 검색 바 | 데스크톱: 검색 바 -->
    <div v-else class="relative flex items-center gap-2 w-full min-w-0 flex-1">
      <div class="relative flex-1 min-w-0">
        <BaseInput
          ref="searchInputRef"
          :id="searchInputId"
          v-model="searchQuery"
          @keydown="handleInputKeyDown"
          @focus="showDropdown = !!searchQuery.trim()"
          :label="$t('search.placeholder')"
          :placeholder="$t('search.placeholder')"
          role="combobox"
          :aria-expanded="showDropdown ? 'true' : 'false'"
          :aria-controls="searchListboxId"
          :aria-activedescendant="activeDescendantId"
          autocomplete="off"
          inputClass="nv-global-search-input w-full min-w-0 rounded-full pl-9 pr-4 py-2 text-sm"
          hideLabel>
          <template #prefix>
            <Search class="nv-global-search-prefix h-4 w-4" />
          </template>
        </BaseInput>
      </div>
      <!-- 모바일 확장 시 접기 버튼 -->
      <button
        v-if="isMobile && isExpanded"
        type="button"
        @click="collapse"
        class="nv-global-search-toggle flex-shrink-0 p-2 rounded-full focus:outline-none"
        :aria-label="$t('common.cancel')">
        <X class="h-5 w-5" />
      </button>
    </div>

    <!-- Autocomplete Dropdown: 모바일 확장 시에만 body에 고정 배치(검색 바 밀림 방지) -->
    <Teleport to="body">
      <div
        v-if="showDropdown && isMobile && isExpanded"
        class="nv-global-search-dropdown fixed inset-x-0 top-16 z-[99] mx-0 rounded-t-none border-t-0 shadow-lg border max-h-96 overflow-y-auto">
        <div v-if="filteredBoards.length > 0">
          <div class="nv-global-search-section nv-global-search-section-text px-3 py-2 text-xs font-semibold">
            {{ $t('search.boards') }}
          </div>
          <ul :id="searchListboxId" role="listbox" aria-label="Board search results">
            <li v-for="(board, index) in filteredBoards" :key="board.boardUrl" @click="selectBoard(board.boardUrl)"
              :id="`${searchListboxId}-${board.boardUrl}`"
              @mouseenter="setSelectedIndex(index)" :class="[
                'nv-global-search-option px-4 py-2 cursor-pointer flex items-center space-x-3',
                { 'nv-global-search-option-selected': index === selectedIndex }
              ]" :aria-selected="index === selectedIndex" role="option" tabindex="-1">
            <div class="nv-global-search-badge flex-shrink-0 h-8 w-8 rounded flex items-center justify-center font-bold overflow-hidden border">
              <img v-if="board.iconUrl" :src="getOptimizedBoardIconUrl(board.iconUrl)" class="nv-global-search-board-icon h-full w-full object-contain" alt="" @error="handleImageError($event)" />
              <span v-else class="text-xs">{{ board.boardName.substring(0, 1) }}</span>
            </div>
            <span class="nv-global-search-board-name text-sm font-medium">{{ board.boardName }}</span>
          </li>
        </ul>
      </div>
        <button type="button" @click="handleSearch" class="nv-global-search-action border-t px-4 py-3 flex w-full items-center text-left">
          <Search class="h-4 w-4 mr-2" />
          <span class="text-sm font-medium">{{ $t('search.doSearch', { query: searchQuery }) }}</span>
        </button>
      </div>
    </Teleport>

    <!-- 데스크톱/접힌 모바일: 컨테이너 안에서 absolute -->
    <div
      v-if="showDropdown && (!isMobile || !isExpanded)"
      class="nv-global-search-dropdown absolute top-full left-0 right-0 z-50 mt-1 w-full min-w-0 sm:min-w-[16rem] rounded-md shadow-lg border max-h-96 overflow-y-auto">
      <div v-if="filteredBoards.length > 0">
        <div class="nv-global-search-section nv-global-search-section-text px-3 py-2 text-xs font-semibold">
          {{ $t('search.boards') }}
        </div>
        <ul :id="searchListboxId" role="listbox" aria-label="Board search results">
          <li v-for="(board, index) in filteredBoards" :key="board.boardUrl" @click="selectBoard(board.boardUrl)"
            :id="`${searchListboxId}-${board.boardUrl}`"
            @mouseenter="setSelectedIndex(index)" :class="[
              'nv-global-search-option px-4 py-2 cursor-pointer flex items-center space-x-3',
              { 'nv-global-search-option-selected': index === selectedIndex }
            ]" :aria-selected="index === selectedIndex" role="option" tabindex="-1">
            <div class="nv-global-search-badge flex-shrink-0 h-8 w-8 rounded flex items-center justify-center font-bold overflow-hidden border">
              <img v-if="board.iconUrl" :src="getOptimizedBoardIconUrl(board.iconUrl)" class="nv-global-search-board-icon h-full w-full object-contain" alt="" @error="handleImageError($event)" />
              <span v-else class="text-xs">{{ board.boardName.substring(0, 1) }}</span>
            </div>
            <span class="nv-global-search-board-name text-sm font-medium">{{ board.boardName }}</span>
          </li>
        </ul>
      </div>
      <button type="button" @click="handleSearch" class="nv-global-search-action border-t px-4 py-3 flex w-full items-center text-left">
        <Search class="h-4 w-4 mr-2" />
        <span class="text-sm font-medium">{{ $t('search.doSearch', { query: searchQuery }) }}</span>
      </button>
    </div>
  </div>
</template>

<style scoped>
.nv-global-search-mobile,
.nv-global-search-dropdown {
  background: color-mix(in srgb, var(--nv-surface) 95%, transparent);
  border-color: var(--nv-line);
}

.nv-global-search-toggle {
  color: var(--nv-ink-soft);
}

.nv-global-search-toggle:hover {
  color: var(--nv-ink);
}

.nv-global-search-toggle:focus-visible {
  box-shadow: 0 0 0 2px color-mix(in srgb, var(--nv-accent) 40%, transparent);
}

.nv-global-search-prefix,
.nv-global-search-section-text {
  color: var(--nv-muted);
}

.nv-global-search-input {
  background: color-mix(in srgb, var(--nv-surface) 98%, transparent);
  border-color: var(--nv-line);
  color: var(--nv-ink);
}

.nv-global-search-input:focus {
  border-color: color-mix(in srgb, var(--nv-accent) 30%, var(--nv-line));
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--nv-accent) 14%, transparent);
}

.nv-global-search-input::placeholder {
  color: var(--nv-muted);
}

.nv-global-search-section {
  background: color-mix(in srgb, var(--nv-surface-2) 78%, transparent);
}

.nv-global-search-action {
  border-color: var(--nv-line);
  color: var(--nv-accent);
  transition: background-color 0.2s ease, color 0.2s ease;
}

.nv-global-search-action:hover {
  background: color-mix(in srgb, var(--nv-surface-2) 82%, transparent);
}

.nv-global-search-board-icon {
  background: color-mix(in srgb, var(--nv-surface) 98%, transparent);
}

.nv-global-search-badge {
  background: color-mix(in srgb, var(--nv-accent-bg) 92%, transparent);
  border-color: color-mix(in srgb, var(--nv-accent) 14%, var(--nv-line));
  color: var(--nv-accent);
}

.nv-global-search-board-name {
  color: var(--nv-ink);
}

.nv-global-search-option {
  transition: background-color 0.2s ease;
}

.nv-global-search-option:hover,
.nv-global-search-option-selected {
  background: color-mix(in srgb, var(--nv-surface-2) 84%, transparent);
}
</style>

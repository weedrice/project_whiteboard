<script setup lang="ts">
import { ref } from 'vue'
import { Search, X } from 'lucide-vue-next'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import BoardAutocompleteList from '@/components/search/BoardAutocompleteList.vue'
import { useBoardAutocomplete } from '@/features/board/search/useBoardAutocomplete'
import { useGlobalSearchController } from '@/composables/useGlobalSearchController'

const searchContainer = ref<HTMLElement | null>(null)
const searchInputRef = ref<{ $el?: HTMLElement; focus?: () => void } | null>(null)
const searchToggleRef = ref<HTMLButtonElement | null>(null)
const searchInputId = 'global-search-input'
const searchListboxId = 'global-search-board-results'
const searchQuery = ref('')
const { filteredBoards, isError, isFetching, retry } = useBoardAutocomplete(searchQuery)

const {
  activeDescendantId,
  collapse,
  expandAndFocus,
  handleInputKeyDown,
  handleSearch,
  isExpanded,
  isMobile,
  selectBoard,
  selectedIndex,
  setSelectedIndex,
  showDropdown,
} = useGlobalSearchController({
  filteredBoards,
  searchQuery,
  searchContainer,
  searchInputRef,
  searchToggleRef,
  searchListboxId
})
</script>

<template>
  <div
    ref="searchContainer"
    class="flex items-center transition-[width] duration-200 ease-out"
    :class="
      isMobile && !isExpanded
        ? 'relative w-11 flex-shrink-0 sm:w-10'
        : isMobile && isExpanded
          ? 'nv-global-search-mobile fixed inset-x-0 top-0 z-[var(--nv-z-popup)] h-16 px-4 shadow-sm border-b'
          : 'relative w-full max-w-[5rem] sm:max-w-xs md:max-w-md'
    ">
    <!-- Mobile collapsed search button -->
    <button
      v-if="isMobile && !isExpanded"
      ref="searchToggleRef"
      type="button"
      @click.stop="expandAndFocus"
      class="nv-global-search-toggle flex h-11 w-11 flex-shrink-0 items-center justify-center rounded-full focus:outline-none sm:h-10 sm:w-10"
      :aria-label="$t('search.placeholder')">
      <Search class="h-5 w-5" />
    </button>
    <!-- Expanded mobile search bar and desktop search input -->
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
      <!-- Mobile collapse button -->
      <button
        v-if="isMobile && isExpanded"
        type="button"
        @click="collapse(true)"
        class="nv-global-search-toggle min-h-11 min-w-11 flex-shrink-0 rounded-full p-2 focus:outline-none"
        :aria-label="$t('common.cancel')">
        <X class="h-5 w-5" />
      </button>
    </div>

    <!-- Mobile autocomplete dropdown is teleported to avoid clipping. -->
    <Teleport to="body">
      <div
        v-if="showDropdown && isMobile && isExpanded"
        class="nv-global-search-dropdown nv-global-search-mobile-results fixed inset-x-0 top-16 z-[var(--nv-z-overlay)] mx-0 rounded-t-none border-t-0 shadow-lg border overflow-y-auto">
        <BoardAutocompleteList
          :boards="filteredBoards"
          :listbox-id="searchListboxId"
          :search-query="searchQuery"
          :selected-index="selectedIndex"
          :is-error="isError"
          :is-fetching="isFetching"
          @highlight="setSelectedIndex"
          @search="handleSearch"
          @select="selectBoard"
          @retry="() => retry()"
        />
      </div>
    </Teleport>

    <!-- Desktop and collapsed mobile autocomplete dropdown -->
    <div
      v-if="showDropdown && (!isMobile || !isExpanded)"
      class="nv-global-search-dropdown absolute top-full left-0 right-0 z-50 mt-1 w-full min-w-0 sm:min-w-[16rem] rounded-md shadow-lg border max-h-96 overflow-y-auto">
      <BoardAutocompleteList
        :boards="filteredBoards"
        :listbox-id="searchListboxId"
        :search-query="searchQuery"
        :selected-index="selectedIndex"
        :is-error="isError"
        :is-fetching="isFetching"
        @highlight="setSelectedIndex"
        @search="handleSearch"
        @select="selectBoard"
        @retry="() => retry()"
      />
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

.nv-global-search-prefix {
  color: var(--nv-muted);
}

.nv-global-search-mobile-results {
  max-height: calc(100vh - 4rem - env(safe-area-inset-bottom));
  max-height: calc(100dvh - 4rem - env(safe-area-inset-bottom));
  overscroll-behavior: contain;
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

</style>

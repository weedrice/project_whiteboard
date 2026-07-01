<script setup lang="ts">
import { computed } from 'vue'
import { Search, X } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import {
  createBoardPostSearchTypeOptions,
  shouldShowBoardPostSearchClear,
} from '@/components/board/boardPostSearchModel'

const props = defineProps<{
  searchQuery: string
  searchType: string
  searchInputElementId: string
  isSearching: boolean
  canWrite: boolean
  boardUrl: string
  transientListError?: string | null
}>()

const emit = defineEmits<{
  (event: 'update:searchQuery', value: string): void
  (event: 'update:searchType', value: string): void
  (event: 'search'): void
  (event: 'clear'): void
}>()

const { t } = useI18n()

const searchTypeOptions = computed(() => createBoardPostSearchTypeOptions(t))
const showClearButton = computed(() => shouldShowBoardPostSearchClear(props.isSearching, props.searchQuery))

const searchQueryModel = computed({
  get: () => props.searchQuery,
  set: (value: string) => emit('update:searchQuery', value)
})

const searchTypeModel = computed({
  get: () => props.searchType,
  set: (value: string) => emit('update:searchType', value)
})
</script>

<template>
  <div class="border-t border-[var(--nv-line)] px-4 py-4 sm:px-5">
    <div class="nv-board-search-row">
      <div class="nv-board-search-group">
        <select
          id="board-search-type"
          v-model="searchTypeModel"
          name="searchType"
          class="nv-board-search-select"
          :aria-label="t('board.detail.searchScopeLabel')"
        >
          <option
            v-for="option in searchTypeOptions"
            :key="option.value"
            :value="option.value"
          >
            {{ option.label }}
          </option>
        </select>

        <div class="nv-board-search-input-wrap">
          <BaseInput
            :id="searchInputElementId"
            v-model="searchQueryModel"
            name="searchQuery"
            autocomplete="off"
            :label="t('board.detail.searchPlaceholder')"
            :aria-label="t('board.detail.searchPlaceholder')"
            :placeholder="t('board.detail.searchPlaceholder')"
            inputClass="nv-board-search-input"
            hideLabel
            @keyup.enter="emit('search')"
          >
            <template #prefix>
              <Search class="h-4 w-4 text-[var(--nv-muted)]" />
            </template>
            <template #suffix>
              <button
                v-if="showClearButton"
                type="button"
                :aria-label="t('board.detail.clearSearch')"
                class="flex cursor-pointer items-center text-[var(--nv-muted)] hover:text-[var(--nv-ink)]"
                @click="emit('clear')"
              >
                <X class="h-4 w-4" />
              </button>
            </template>
          </BaseInput>
        </div>

        <BaseButton @click="emit('search')" variant="secondary" type="button" class="nv-board-search-btn">
          {{ t('search.doSearch') }}
        </BaseButton>
      </div>

      <router-link
        v-if="canWrite"
        :to="`/board/${boardUrl}/write`"
        class="nv-board-write-btn nv-board-search-write-btn"
      >
        {{ t('common.write') }}
      </router-link>
    </div>

    <p v-if="transientListError" class="mt-2 text-center text-xs nv-form-error">
      {{ transientListError }}
    </p>
  </div>
</template>

<style scoped>
.nv-board-search-row {
  align-items: center;
  display: grid;
  gap: 0.75rem;
  grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
  width: 100%;
}

.nv-board-search-group {
  display: grid;
  grid-column: 2;
  gap: 0.5rem;
  grid-template-columns: minmax(6.1rem, 7.25rem) minmax(0, 20rem) auto;
  justify-self: center;
  width: min(100%, 34rem);
}

.nv-board-search-select,
:deep(.nv-board-search-input) {
  background: color-mix(in srgb, var(--nv-surface) 96%, transparent);
  border: 1px solid var(--nv-line);
  border-color: var(--nv-line);
  border-radius: 0.45rem;
  color: var(--nv-ink);
  height: 2.2rem;
  line-height: 1;
  min-height: 2.2rem;
}

.nv-board-search-select {
  font-size: 0.74rem;
  font-weight: 500;
  outline: none;
  padding: 0 0.55rem;
}

:deep(.nv-board-search-input) {
  padding-block: 0;
}

.nv-board-search-select:focus,
:deep(.nv-board-search-input:focus) {
  border-color: color-mix(in srgb, var(--nv-accent) 30%, var(--nv-line));
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--nv-accent) 14%, transparent);
}

.nv-board-search-input-wrap {
  min-width: 0;
}

.nv-board-search-btn {
  background: var(--nv-accent);
  border-color: var(--nv-accent);
  border-radius: 0.45rem;
  color: #fff;
  height: 2.2rem;
  line-height: 1;
  min-height: 2.2rem;
  min-width: 3.9rem;
  padding-inline: 0.6rem;
  padding-block: 0;
  transition: filter 0.2s ease, box-shadow 0.2s ease;
  white-space: nowrap;
}

.nv-board-search-btn:hover {
  filter: brightness(0.94);
}

.nv-board-search-write-btn {
  grid-column: 3;
  justify-self: end;
  white-space: nowrap;
}

.nv-board-write-btn {
  align-items: center;
  background: var(--nv-accent);
  border: 1px solid var(--nv-accent);
  border-radius: 0.55rem;
  color: #fff;
  display: inline-flex;
  font-size: 0.82rem;
  font-weight: 600;
  height: 2.2rem;
  justify-content: center;
  line-height: 1;
  min-height: 2.2rem;
  min-width: 4.75rem;
  padding: 0 0.8rem;
  transition: background-color 0.2s ease, border-color 0.2s ease, color 0.2s ease, filter 0.2s ease, box-shadow 0.2s ease;
}

.nv-board-write-btn:hover {
  filter: brightness(0.94);
}

.nv-board-write-btn,
.nv-board-search-btn:not(:disabled) {
  cursor: pointer;
}

.nv-board-write-btn:active,
.nv-board-search-btn:not(:disabled):active {
  filter: brightness(0.9);
}

.nv-board-write-btn:focus-visible,
.nv-board-search-btn:focus-visible {
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--nv-accent) 18%, transparent);
  outline: none;
}

.nv-board-search-btn:disabled {
  cursor: not-allowed;
}

@media (max-width: 1023px) {
  .nv-board-search-row {
    grid-template-columns: 1fr;
  }

  .nv-board-search-group {
    grid-column: 1;
    grid-template-columns: 1fr;
    justify-self: center;
    width: min(100%, 24rem);
  }

  .nv-board-search-write-btn {
    grid-column: 1;
    justify-self: center;
    width: min(100%, 24rem);
  }
}
</style>

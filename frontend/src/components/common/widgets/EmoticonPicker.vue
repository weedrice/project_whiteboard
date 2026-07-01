<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type { EmoticonImage } from '@/types/emoticon'
import { X, ArrowLeft, Search, Smile } from 'lucide-vue-next'
import { useAccessibleEmoticonPicker } from '@/composables/useAccessibleEmoticonPicker'
import { useEmoticonPickerDialogLifecycle } from '@/composables/useEmoticonPickerDialogLifecycle'
import { useEmoticonPickerDetail } from '@/composables/useEmoticonPickerDetail'
import { useEmoticonPickerSearch } from '@/composables/useEmoticonPickerSearch'
import BaseSpinner from '@/components/common/ui/BaseSpinner.vue'
import EmoticonPickerGrid from '@/components/common/widgets/EmoticonPickerGrid.vue'
import EmoticonPickerImageGrid from '@/components/common/widgets/EmoticonPickerImageGrid.vue'

const props = defineProps<{
  show: boolean
}>()

const emit = defineEmits<{
  (e: 'select', image: EmoticonImage): void
  (e: 'close'): void
}>()
const { t } = useI18n()

const pickerRef = ref<HTMLElement | null>(null)
const {
  selectedEmoticon,
  selectedEmoticonId,
  selectedImages,
  isLoadingDetail,
  detailError,
  resetDetailState,
  handleEmoticonClick,
  retryDetailLoad,
} = useEmoticonPickerDetail(t)

const {
  data: accessibleEmoticons,
  isLoading,
  isError: isListError,
  refetch: refetchAccessibleEmoticons
} = useAccessibleEmoticonPicker(() => props.show)
const listErrorMessage = computed(() => t('emoticon.picker.listLoadFailed'))
const { searchKeyword, filteredEmoticons, clearSearch } = useEmoticonPickerSearch(accessibleEmoticons)

const resetPickerState = (options: { clearSearch?: boolean } = {}) => {
  resetDetailState()
  if (options.clearSearch) {
    clearSearch()
  }
}

const retryListLoad = () => {
  refetchAccessibleEmoticons()
}

const handleImageClick = (image: EmoticonImage) => {
  emit('select', image)
}

const goBack = () => {
  resetPickerState()
}

const close = () => {
  resetPickerState({ clearSearch: true })
  emit('close')
}

useEmoticonPickerDialogLifecycle({
  isOpen: () => props.show,
  dialogRef: pickerRef,
  close,
  reset: () => resetPickerState({ clearSearch: true }),
})
</script>

<template>
  <div v-if="show" class="emoticon-picker-backdrop" @click="close" aria-hidden="true" />
  <div
    v-if="show"
    ref="pickerRef"
    class="emoticon-picker"
    role="dialog"
    aria-modal="true"
    aria-labelledby="emoticon-picker-title"
    @click.stop
  >
    <div class="picker-header">
      <button v-if="selectedEmoticonId" type="button" :aria-label="t('emoticon.picker.backToListAria')" @click="goBack" class="back-btn">
        <ArrowLeft class="w-4 h-4" />
      </button>
      <span id="emoticon-picker-title" class="header-title">
        {{ selectedEmoticon?.name || t('emoticon.title') }}
      </span>
      <button type="button" :aria-label="t('emoticon.picker.closeAria')" @click="close" class="close-btn">
        <X class="w-4 h-4" />
      </button>
    </div>

    <div class="picker-content">
      <template v-if="selectedEmoticonId">
        <div v-if="isLoadingDetail" class="loading-state">
          <div class="h-6 w-6 flex items-center justify-center">
            <BaseSpinner size="sm" class="scale-150" />
          </div>
        </div>
        <EmoticonPickerImageGrid
          v-else-if="selectedEmoticon && selectedImages.length > 0"
          :images="selectedImages"
          :emoticon-name="selectedEmoticon.name"
          @select="handleImageClick"
        />
        <div v-else-if="selectedEmoticon" class="empty-state">
          <Smile class="w-8 h-8 nv-text-subtle mb-2" />
          <p>{{ t('emoticon.detail.imageEmpty') }}</p>
        </div>
        <div v-else-if="detailError" class="error-state">
          <p>{{ detailError }}</p>
          <div class="error-actions">
            <button type="button" class="retry-btn" @click="retryDetailLoad">{{ t('common.error.retry') }}</button>
            <button type="button" class="retry-btn secondary" @click="goBack">{{ t('emoticon.detail.backToList') }}</button>
          </div>
        </div>
      </template>

      <template v-else-if="!selectedEmoticonId">
        <div class="search-area">
          <div class="relative">
            <Search class="absolute left-2 top-1/2 -translate-y-1/2 w-4 h-4 nv-text-subtle" />
            <input v-model="searchKeyword" type="text" :aria-label="t('emoticon.picker.searchAria')" :placeholder="t('search.placeholder')" class="search-input" />
          </div>
        </div>

        <div v-if="isLoading" class="loading-state">
          <div class="h-6 w-6 flex items-center justify-center">
            <BaseSpinner size="sm" class="scale-150" />
          </div>
        </div>

        <div v-else-if="isListError" class="error-state">
          <p>{{ listErrorMessage }}</p>
          <button type="button" class="retry-btn" @click="retryListLoad">{{ t('common.error.retry') }}</button>
        </div>

        <div v-else-if="!filteredEmoticons?.length" class="empty-state">
          <Smile class="w-8 h-8 nv-text-subtle mb-2" />
          <p v-if="accessibleEmoticons?.length === 0">{{ t('emoticon.picker.availableEmpty') }}</p>
          <p v-else>{{ t('common.messages.noResults') }}</p>
        </div>

        <EmoticonPickerGrid
          v-else
          :emoticons="filteredEmoticons"
          @select="handleEmoticonClick"
        />
      </template>
    </div>
  </div>
</template>

<style scoped>
.emoticon-picker-backdrop {
  position: fixed;
  inset: 0;
  z-index: 99;
  cursor: default;
}

.emoticon-picker {
  position: absolute;
  top: 42px;
  right: 0;
  width: min(400px, calc(100vw - 24px));
  max-width: 100%;
  max-height: min(450px, 80vh);
  background: var(--nv-surface);
  border-radius: 8px;
  box-shadow: var(--nv-shadow-popup);
  color: var(--nv-text);
  z-index: 100;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

@media (max-width: 639px) {
  .emoticon-picker {
    position: fixed;
    top: 50%;
    left: 50%;
    right: auto;
    transform: translate(-50%, -50%);
    width: min(360px, calc(100vw - 24px));
    max-height: min(420px, calc(100vh - 32px));
  }
}

.picker-header {
  display: flex;
  align-items: center;
  padding: 10px 12px;
  border-bottom: 1px solid var(--nv-border);
  gap: 8px;
  flex-shrink: 0;
}

@media (max-width: 639px) {
  .picker-header {
    padding: 8px 10px;
  }
}

.header-title {
  flex: 1;
  font-weight: 600;
  font-size: 14px;
  color: var(--nv-text);
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 639px) {
  .header-title {
    font-size: 13px;
  }
}

.back-btn,
.close-btn {
  padding: 4px;
  border-radius: 4px;
  color: var(--nv-text-muted);
  cursor: pointer;
  transition: all 0.2s;
}

.back-btn:hover,
.close-btn:hover {
  background: var(--nv-surface-hover);
  color: var(--nv-text);
}

.picker-content {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
  min-height: 0;
}

@media (max-width: 639px) {
  .picker-content {
    padding: 8px 10px;
  }
}

.search-area {
  margin-bottom: 12px;
}

.search-input {
  width: 100%;
  padding: 8px 8px 8px 32px;
  background: var(--nv-surface);
  border: 1px solid var(--nv-border);
  border-radius: 6px;
  color: var(--nv-text);
  font-size: 13px;
  outline: none;
  transition: border-color 0.2s, box-shadow 0.2s;
}

.search-input::placeholder {
  color: var(--nv-text-subtle);
}

.search-input:focus {
  border-color: var(--nv-focus);
  box-shadow: 0 0 0 2px color-mix(in srgb, var(--nv-focus) 28%, transparent);
}

.loading-state {
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 40px 0;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 0;
  color: var(--nv-text-subtle);
  font-size: 13px;
}

.error-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 40px 0;
  color: var(--nv-danger-text);
  font-size: 13px;
  text-align: center;
}

.error-actions {
  display: flex;
  gap: 8px;
}

.retry-btn {
  border-radius: 6px;
  background: var(--nv-accent);
  color: white;
  padding: 6px 10px;
  font-size: 12px;
  font-weight: 600;
}

.retry-btn.secondary {
  background: var(--nv-surface-muted);
  color: var(--nv-text-muted);
}

.retry-btn:hover {
  background: color-mix(in srgb, var(--nv-accent) 88%, black 12%);
}

.retry-btn.secondary:hover {
  background: var(--nv-surface-hover);
  color: var(--nv-text);
}

</style>

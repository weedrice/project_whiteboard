<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type { EmoticonImage } from '@/types/emoticon'
import { X, ArrowLeft, Search } from 'lucide-vue-next'
import { useAccessibleEmoticonPicker } from '@/composables/useAccessibleEmoticonPicker'
import { useEmoticonPickerDialogLifecycle } from '@/features/emoticon/picker/useEmoticonPickerDialogLifecycle'
import { useEmoticonPickerDetail } from '@/features/emoticon/picker/useEmoticonPickerDetail'
import { useEmoticonPickerSearch } from '@/features/emoticon/picker/useEmoticonPickerSearch'
import EmoticonPickerGrid from '@/components/common/widgets/EmoticonPickerGrid.vue'
import EmoticonPickerImageGrid from '@/components/common/widgets/EmoticonPickerImageGrid.vue'
import EmoticonPickerStatePanel from '@/components/common/widgets/EmoticonPickerStatePanel.vue'
import BaseInput from '@/components/common/ui/BaseInput.vue'

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
        <EmoticonPickerStatePanel v-if="isLoadingDetail" state="loading" />
        <EmoticonPickerImageGrid
          v-else-if="selectedEmoticon && selectedImages.length > 0"
          :images="selectedImages"
          :emoticon-name="selectedEmoticon.name"
          @select="handleImageClick"
        />
        <EmoticonPickerStatePanel
          v-else-if="selectedEmoticon"
          state="empty"
          :message="t('emoticon.detail.imageEmpty')"
        />
        <EmoticonPickerStatePanel
          v-else-if="detailError"
          state="error"
          :message="detailError"
          :retry-label="t('common.error.retry')"
          :back-label="t('emoticon.detail.backToList')"
          @retry="retryDetailLoad"
          @back="goBack"
        />
      </template>

      <template v-else-if="!selectedEmoticonId">
        <div class="search-area">
          <BaseInput
            v-model="searchKeyword"
            type="text"
            :label="t('emoticon.picker.searchAria')"
            :placeholder="t('search.placeholder')"
            input-class="search-input"
            hide-label
          >
            <template #prefix>
              <Search class="h-4 w-4 nv-text-subtle" />
            </template>
          </BaseInput>
        </div>

        <EmoticonPickerStatePanel v-if="isLoading" state="loading" />

        <EmoticonPickerStatePanel
          v-else-if="isListError"
          state="error"
          :message="listErrorMessage"
          :retry-label="t('common.error.retry')"
          @retry="retryListLoad"
        />

        <EmoticonPickerStatePanel
          v-else-if="!filteredEmoticons?.length"
          state="empty"
          :message="accessibleEmoticons?.length === 0 ? t('emoticon.picker.availableEmpty') : t('common.messages.noResults')"
        />

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
  align-items: center;
  display: inline-flex;
  justify-content: center;
  min-height: 44px;
  min-width: 44px;
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

:deep(.search-input) {
  width: 100%;
  padding: 8px 8px 8px 32px;
  background: var(--nv-surface);
  border: 1px solid var(--nv-border);
  border-radius: 6px;
  color: var(--nv-text);
  font-size: 13px;
  min-height: 44px;
  outline: none;
  transition: border-color 0.2s, box-shadow 0.2s;
}

:deep(.search-input::placeholder) {
  color: var(--nv-text-subtle);
}

:deep(.search-input:focus) {
  border-color: var(--nv-focus);
  box-shadow: 0 0 0 2px color-mix(in srgb, var(--nv-focus) 28%, transparent);
}

</style>

<script setup lang="ts">
import { ref, computed, onUnmounted, watch } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { emoticonApi } from '@/api/emoticon'
import type { EmoticonMaster, EmoticonImage } from '@/types/emoticon'
import { X, ArrowLeft, Search, Smile } from 'lucide-vue-next'
import logger from '@/utils/logger'
import { DEFAULT_EMOTICON_IMAGE_URL, applyImageFallback } from '@/utils/imageFallback'
import { useLatestAsyncTask } from '@/composables/useLatestAsyncTask'
import { accessibleEmoticonPickerQueryKey } from '@/composables/useEmoticonEditResource'
import BaseSpinner from '@/components/common/ui/BaseSpinner.vue'

const props = defineProps<{
  show: boolean
}>()

const emit = defineEmits<{
  (e: 'select', image: EmoticonImage): void
  (e: 'close'): void
}>()

const selectedEmoticon = ref<EmoticonMaster | null>(null)
const selectedEmoticonId = ref<number | null>(null)
const selectedEmoticonSource = ref<EmoticonMaster | null>(null)
const searchKeyword = ref('')
const detailTask = useLatestAsyncTask<string>({
  getErrorValue: () => '노비콘 정보를 불러오지 못했습니다.',
  onError: (error) => {
    logger.error('Failed to load emoticon detail:', error)
  },
})
const isLoadingDetail = detailTask.loading
const detailError = detailTask.error

const mergeUniqueEmoticons = (...groups: EmoticonMaster[][]) => {
  const seen = new Set<number>()
  return groups.flat().filter((emoticon) => {
    if (seen.has(emoticon.emoticonId)) {
      return false
    }
    seen.add(emoticon.emoticonId)
    return true
  })
}

// 사용 가능한 이모티콘 목록 조회
const {
  data: accessibleEmoticons,
  isLoading,
  isError: isListError,
  refetch: refetchAccessibleEmoticons
} = useQuery({
  queryKey: accessibleEmoticonPickerQueryKey,
  queryFn: async () => {
    const [purchasedPage, myPage] = await Promise.all([
      emoticonApi.getPurchasedEmoticonsData({ size: 100 }),
      emoticonApi.getMyEmoticonsData({ size: 100 }),
    ])
    return mergeUniqueEmoticons(purchasedPage.content, myPage.content)
  },
  enabled: () => props.show
})
const listErrorMessage = '노비콘 목록을 불러오지 못했습니다.'

// 검색 필터링
const filteredEmoticons = computed(() => {
  if (!accessibleEmoticons.value) return []
  if (!searchKeyword.value.trim()) return accessibleEmoticons.value

  const keyword = searchKeyword.value.toLowerCase()
  return accessibleEmoticons.value.filter(emoticon =>
    emoticon.name.toLowerCase().includes(keyword) ||
    emoticon.tags?.some(tag => tag.toLowerCase().includes(keyword))
  )
})

// 선택된 이모티콘의 이미지 목록
const selectedImages = computed(() => {
  return selectedEmoticon.value?.images || []
})

const resetDetailState = (options: { clearSearch?: boolean } = {}) => {
  detailTask.reset()
  selectedEmoticon.value = null
  selectedEmoticonId.value = null
  selectedEmoticonSource.value = null
  if (options.clearSearch) {
    searchKeyword.value = ''
  }
}

const handleEmoticonClick = async (emoticon: EmoticonMaster) => {
  // 상세 정보 조회 (이미지 포함)
  selectedEmoticonId.value = emoticon.emoticonId
  selectedEmoticonSource.value = emoticon
  selectedEmoticon.value = null

  const emoticonDetail = await detailTask.run(({ signal }) => emoticonApi.getEmoticonData(emoticon.emoticonId, { signal }))
  if (!emoticonDetail || selectedEmoticonId.value !== emoticon.emoticonId) return
  selectedEmoticon.value = emoticonDetail
}

const retryDetailLoad = () => {
  if (!selectedEmoticonSource.value) return
  handleEmoticonClick(selectedEmoticonSource.value)
}

const retryListLoad = () => {
  refetchAccessibleEmoticons()
}

const handleImageClick = (image: EmoticonImage) => {
  emit('select', image)
}

const goBack = () => {
  resetDetailState()
}

const close = () => {
  resetDetailState({ clearSearch: true })
  emit('close')
}

// 팝업이 닫힐 때 상태 초기화
watch(() => props.show, (newVal) => {
  if (!newVal) {
    resetDetailState({ clearSearch: true })
  }
})

onUnmounted(() => {
  resetDetailState({ clearSearch: true })
})
</script>

<template>
  <!-- 팝업 밖 클릭 시 닫기 위한 투명 백드롭 -->
  <div v-if="show" class="emoticon-picker-backdrop" @click="close" aria-hidden="true" />
  <div
    v-if="show"
    class="emoticon-picker"
    role="dialog"
    aria-modal="true"
    aria-labelledby="emoticon-picker-title"
    @click.stop
  >
    <!-- 헤더 -->
    <div class="picker-header">
      <button v-if="selectedEmoticonId" type="button" aria-label="이모티콘 목록으로 돌아가기" @click="goBack" class="back-btn">
        <ArrowLeft class="w-4 h-4" />
      </button>
      <span id="emoticon-picker-title" class="header-title">
        {{ selectedEmoticon?.name || '노비콘' }}
      </span>
      <button type="button" aria-label="이모티콘 선택기 닫기" @click="close" class="close-btn">
        <X class="w-4 h-4" />
      </button>
    </div>

    <!-- 컨텐츠 -->
    <div class="picker-content">
      <!-- 이모티콘 상세 (이미지 목록) -->
      <template v-if="selectedEmoticonId">
        <!-- 상세 로딩 중 -->
        <div v-if="isLoadingDetail" class="loading-state">
          <div class="h-6 w-6 flex items-center justify-center">
            <BaseSpinner size="sm" class="scale-150" />
          </div>
        </div>
        <!-- 이미지 그리드 -->
        <div v-else-if="selectedEmoticon" class="images-grid">
          <button
            v-for="image in selectedImages"
            :key="image.imageId"
            type="button"
            :aria-label="`${selectedEmoticon.name} 이미지 선택`"
            @click="handleImageClick(image)"
            class="image-btn"
          >
            <img :src="image.imageUrl || DEFAULT_EMOTICON_IMAGE_URL" :alt="selectedEmoticon.name" @error="applyImageFallback" />
          </button>
        </div>
        <div v-else-if="detailError" class="error-state">
          <p>{{ detailError }}</p>
          <div class="error-actions">
            <button type="button" class="retry-btn" @click="retryDetailLoad">다시 시도</button>
            <button type="button" class="retry-btn secondary" @click="goBack">목록으로</button>
          </div>
        </div>
      </template>

      <!-- 이모티콘 목록 -->
      <template v-else-if="!selectedEmoticonId">
        <!-- 검색 -->
        <div class="search-area">
          <div class="relative">
            <Search class="absolute left-2 top-1/2 -translate-y-1/2 w-4 h-4 nv-text-subtle" />
            <input v-model="searchKeyword" type="text" aria-label="노비콘 검색" placeholder="검색..." class="search-input" />
          </div>
        </div>

        <!-- 로딩 -->
        <div v-if="isLoading" class="loading-state">
          <div class="h-6 w-6 flex items-center justify-center">
            <BaseSpinner size="sm" class="scale-150" />
          </div>
        </div>

        <div v-else-if="isListError" class="error-state">
          <p>{{ listErrorMessage }}</p>
          <button type="button" class="retry-btn" @click="retryListLoad">다시 시도</button>
        </div>

        <!-- 빈 상태 -->
        <div v-else-if="!filteredEmoticons?.length" class="empty-state">
          <Smile class="w-8 h-8 nv-text-subtle mb-2" />
          <p v-if="accessibleEmoticons?.length === 0">사용 가능한 노비콘이 없습니다</p>
          <p v-else>검색 결과가 없습니다</p>
        </div>

        <!-- 이모티콘 목록 -->
        <div v-else class="emoticons-grid">
          <button v-for="emoticon in filteredEmoticons" :key="emoticon.emoticonId" type="button"
            @click="handleEmoticonClick(emoticon)" class="emoticon-btn">
            <img
              :src="emoticon.thumbnailUrl || emoticon.images?.[0]?.imageUrl || DEFAULT_EMOTICON_IMAGE_URL"
              :alt="emoticon.name"
              @error="applyImageFallback"
            />
            <span class="emoticon-name">{{ emoticon.name }}</span>
          </button>
        </div>
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

/* 모바일: 뷰포트 기준 고정 위치로 화면 밖 이탈 방지 */
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

.emoticons-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
}

@media (max-width: 639px) {
  .emoticons-grid {
    grid-template-columns: repeat(2, 1fr);
    gap: 6px;
  }
}

.emoticon-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 8px;
  border-radius: 8px;
  cursor: pointer;
  transition: background-color 0.2s;
}

@media (max-width: 639px) {
  .emoticon-btn {
    padding: 6px;
    border-radius: 6px;
  }
}

.emoticon-btn:hover {
  background: var(--nv-surface-hover);
}

.emoticon-btn img {
  width: 64px;
  height: 64px;
  object-fit: contain;
  border-radius: 4px;
}

@media (max-width: 639px) {
  .emoticon-btn img {
    width: 48px;
    height: 48px;
  }
}

.emoticon-name {
  margin-top: 4px;
  font-size: 11px;
  color: var(--nv-text-muted);
  text-align: center;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 100%;
}

@media (max-width: 639px) {
  .emoticon-name {
    font-size: 10px;
    margin-top: 2px;
  }
}

.images-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
}

@media (max-width: 639px) {
  .images-grid {
    grid-template-columns: repeat(3, 1fr);
    gap: 6px;
  }
}

.image-btn {
  padding: 6px;
  border-radius: 6px;
  cursor: pointer;
  transition: background-color 0.2s;
}

.image-btn:hover {
  background: var(--nv-surface-hover);
}

.image-btn img {
  width: 100%;
  aspect-ratio: 1;
  object-fit: contain;
}

@media (max-width: 639px) {
  .image-btn {
    padding: 4px;
    border-radius: 4px;
  }
}
</style>

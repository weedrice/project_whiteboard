<script setup lang="ts">
import { ref, computed, onUnmounted, watch } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { emoticonApi } from '@/api/emoticon'
import type { EmoticonMaster, EmoticonImage } from '@/types/emoticon'
import { X, ArrowLeft, Search, Smile } from 'lucide-vue-next'
import logger from '@/utils/logger'
import { DEFAULT_EMOTICON_IMAGE_URL, applyImageFallback } from '@/utils/imageFallback'

const props = defineProps<{
  show: boolean
}>()

const emit = defineEmits<{
  (e: 'select', image: EmoticonImage): void
  (e: 'close'): void
}>()

const selectedEmoticon = ref<EmoticonMaster | null>(null)
const selectedEmoticonId = ref<number | null>(null)
const searchKeyword = ref('')
const isLoadingDetail = ref(false)
let detailRequestId = 0
let detailAbortController: AbortController | null = null

// 구매한 이모티콘 목록 조회
const { data: purchasedEmoticons, isLoading } = useQuery({
  queryKey: ['emoticons', 'purchased', 'picker'],
  queryFn: async () => {
    const purchasedPage = await emoticonApi.getPurchasedEmoticonsData({ size: 100 })
    return purchasedPage.content
  },
  enabled: () => props.show
})

// 검색 필터링
const filteredEmoticons = computed(() => {
  if (!purchasedEmoticons.value) return []
  if (!searchKeyword.value.trim()) return purchasedEmoticons.value

  const keyword = searchKeyword.value.toLowerCase()
  return purchasedEmoticons.value.filter(emoticon =>
    emoticon.name.toLowerCase().includes(keyword) ||
    emoticon.tags?.some(tag => tag.toLowerCase().includes(keyword))
  )
})

// 선택된 이모티콘의 이미지 목록
const selectedImages = computed(() => {
  return selectedEmoticon.value?.images || []
})

const resetDetailState = (options: { clearSearch?: boolean } = {}) => {
  detailRequestId++
  detailAbortController?.abort()
  detailAbortController = null
  isLoadingDetail.value = false
  selectedEmoticon.value = null
  selectedEmoticonId.value = null
  if (options.clearSearch) {
    searchKeyword.value = ''
  }
}

const handleEmoticonClick = async (emoticon: EmoticonMaster) => {
  // 상세 정보 조회 (이미지 포함)
  detailAbortController?.abort()
  const controller = new AbortController()
  detailAbortController = controller
  const requestId = ++detailRequestId
  isLoadingDetail.value = true
  selectedEmoticonId.value = emoticon.emoticonId

  try {
    const emoticonDetail = await emoticonApi.getEmoticonData(emoticon.emoticonId, {
      signal: controller.signal,
    })
    if (requestId !== detailRequestId || selectedEmoticonId.value !== emoticon.emoticonId) {
      return
    }
    selectedEmoticon.value = emoticonDetail
  } catch (error) {
    if (!controller.signal.aborted && requestId === detailRequestId && selectedEmoticonId.value === emoticon.emoticonId) {
      logger.error('Failed to load emoticon detail:', error)
    }
  } finally {
    if (detailAbortController === controller) {
      detailAbortController = null
    }
    if (requestId === detailRequestId && selectedEmoticonId.value === emoticon.emoticonId) {
      isLoadingDetail.value = false
    }
  }
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
          <div class="animate-spin rounded-full h-6 w-6 border-b-2 border-indigo-500"></div>
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
      </template>

      <!-- 이모티콘 목록 -->
      <template v-else-if="!selectedEmoticonId">
        <!-- 검색 -->
        <div class="search-area">
          <div class="relative">
            <Search class="absolute left-2 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
            <input v-model="searchKeyword" type="text" aria-label="노비콘 검색" placeholder="검색..." class="search-input" />
          </div>
        </div>

        <!-- 로딩 -->
        <div v-if="isLoading" class="loading-state">
          <div class="animate-spin rounded-full h-6 w-6 border-b-2 border-indigo-500"></div>
        </div>

        <!-- 빈 상태 -->
        <div v-else-if="!filteredEmoticons?.length" class="empty-state">
          <Smile class="w-8 h-8 text-gray-400 mb-2" />
          <p v-if="purchasedEmoticons?.length === 0">구매한 노비콘이 없습니다</p>
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
  background: white;
  border-radius: 8px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.15);
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

.dark .emoticon-picker {
  background: #1f2937;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.4);
}

.picker-header {
  display: flex;
  align-items: center;
  padding: 10px 12px;
  border-bottom: 1px solid #e5e7eb;
  gap: 8px;
  flex-shrink: 0;
}

@media (max-width: 639px) {
  .picker-header {
    padding: 8px 10px;
  }
}

.dark .picker-header {
  border-bottom-color: #374151;
}

.header-title {
  flex: 1;
  font-weight: 600;
  font-size: 14px;
  color: #1f2937;
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

.dark .header-title {
  color: #f3f4f6;
}

.back-btn,
.close-btn {
  padding: 4px;
  border-radius: 4px;
  color: #6b7280;
  cursor: pointer;
  transition: all 0.2s;
}

.back-btn:hover,
.close-btn:hover {
  background: #f3f4f6;
  color: #1f2937;
}

.dark .back-btn:hover,
.dark .close-btn:hover {
  background: #374151;
  color: #f3f4f6;
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
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  font-size: 13px;
  outline: none;
  transition: border-color 0.2s;
}

.search-input:focus {
  border-color: #6366f1;
}

.dark .search-input {
  background: #374151;
  border-color: #4b5563;
  color: #f3f4f6;
}

.dark .search-input:focus {
  border-color: #6366f1;
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
  color: #6b7280;
  font-size: 13px;
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
  background: #f3f4f6;
}

.dark .emoticon-btn:hover {
  background: #374151;
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
  color: #6b7280;
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

.dark .emoticon-name {
  color: #9ca3af;
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
  background: #f3f4f6;
}

.dark .image-btn:hover {
  background: #374151;
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

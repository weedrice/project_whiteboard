<script setup lang="ts">
import { ref, computed, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { emoticonApi } from '@/api/emoticon'
import { fileApi } from '@/api/file'
import { useHead } from '@unhead/vue'
import { ArrowLeft, Upload, X, Plus } from 'lucide-vue-next'
import { useToastStore } from '@/stores/toast'
import { useI18n } from 'vue-i18n'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import { extractErrorMessage } from '@/utils/errorHandler'
import { useEmoticonImageSelection } from '@/composables/useEmoticonImageSelection'
import {
  resolveEmoticonTagAddition,
  revokeEmoticonPreviewUrl,
  SUPPORTED_EMOTICON_IMAGE_ACCEPT,
  uploadEmoticonImagePreviews,
  type EmoticonImagePreview
} from '@/utils/emoticonImage'

const { t } = useI18n()
const router = useRouter()
const toastStore = useToastStore()
const { selectThumbnailImage, selectEmoticonImages } = useEmoticonImageSelection(t, toastStore)

useHead({
  title: '노비콘 등록'
})

// 폼 상태
const emoticonName = ref('')
const thumbnailFile = ref<File | null>(null)
const thumbnailPreview = ref<string | null>(null)
const emoticonPreviews = ref<EmoticonImagePreview[]>([])
const tagInput = ref('')
const isSubmitting = ref(false)
const uploadProgress = ref({ current: 0, total: 0 })
const uploadControllers = new Set<AbortController>()
let submitRunId = 0
let isComponentUnmounted = false
let tagSequence = 0

interface EmoticonTagItem {
  clientId: string
  value: string
}

const createTagItem = (value: string): EmoticonTagItem => {
  tagSequence += 1
  return {
    clientId: `emoticon-tag-${tagSequence}`,
    value
  }
}

const tagItems = ref<EmoticonTagItem[]>([])
const tags = computed<string[]>({
  get: () => tagItems.value.map((item) => item.value),
  set: (values) => {
    tagItems.value = values.map(createTagItem)
  }
})

// 파일 입력 refs
const thumbnailInput = ref<HTMLInputElement | null>(null)
const emoticonInput = ref<HTMLInputElement | null>(null)

const SUPPORTED_IMAGE_ACCEPT = SUPPORTED_EMOTICON_IMAGE_ACCEPT

const createUploadCancelledError = () => new DOMException('Upload has been cancelled', 'AbortError')

const isUploadCancelledError = (error: unknown) => {
  if (!isComponentUnmounted) {
    return false
  }

  if (error instanceof DOMException && error.name === 'AbortError') {
    return true
  }

  if (typeof error !== 'object' || error === null) {
    return false
  }

  const maybeCancelledError = error as { code?: string; name?: string }
  return maybeCancelledError.code === 'ERR_CANCELED' || maybeCancelledError.name === 'AbortError'
}

const assertSubmitActive = (runId: number) => {
  if (isComponentUnmounted || submitRunId !== runId) {
    throw createUploadCancelledError()
  }
}

const abortPendingUploads = () => {
  uploadControllers.forEach((controller) => controller.abort())
  uploadControllers.clear()
}

const createUploadController = () => {
  const controller = new AbortController()
  uploadControllers.add(controller)
  return controller
}

onUnmounted(() => {
  isComponentUnmounted = true
  submitRunId += 1
  abortPendingUploads()
  revokeEmoticonPreviewUrl(thumbnailPreview.value)
  emoticonPreviews.value.forEach((item) => {
    revokeEmoticonPreviewUrl(item.preview)
  })
})

// 썸네일 선택
const handleThumbnailSelect = async (event: Event) => {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return

  const selectedThumbnail = await selectThumbnailImage(file)
  if (!selectedThumbnail) return

  revokeEmoticonPreviewUrl(thumbnailPreview.value)
  thumbnailFile.value = file
  thumbnailPreview.value = selectedThumbnail.preview
}

// 썸네일 제거
const removeThumbnail = () => {
  revokeEmoticonPreviewUrl(thumbnailPreview.value)
  thumbnailFile.value = null
  thumbnailPreview.value = null
  if (thumbnailInput.value) {
    thumbnailInput.value.value = ''
  }
}

// 이모티콘 이미지 선택
const handleEmoticonSelect = async (event: Event) => {
  const input = event.target as HTMLInputElement
  const files = input.files
  if (!files) return

  const remainingSlots = 100 - emoticonPreviews.value.length
  const selectedImages = await selectEmoticonImages(files, remainingSlots)
  emoticonPreviews.value.push(...selectedImages)

  // 입력 초기화
  if (emoticonInput.value) {
    emoticonInput.value.value = ''
  }
}

// 이모티콘 이미지 제거
const removeEmoticonImage = (clientId: string) => {
  const index = emoticonPreviews.value.findIndex((item) => item.clientId === clientId)
  const item = index >= 0 ? emoticonPreviews.value[index] : null
  if (item) {
    revokeEmoticonPreviewUrl(item.preview)
    emoticonPreviews.value.splice(index, 1)
  }
}

// 태그 추가
const addTag = () => {
  const result = resolveEmoticonTagAddition(tagInput.value, tags.value)
  if (result.error === 'maxTags') {
    toastStore.addToast(t('emoticon.validation.maxTags'), 'error')
  } else if (result.tag) {
    tagItems.value.push(createTagItem(result.tag))
  }
  tagInput.value = ''
}

// 태그 제거
const removeTag = (clientId: string) => {
  const index = tagItems.value.findIndex((item) => item.clientId === clientId)
  if (index >= 0) {
    tagItems.value.splice(index, 1)
  }
}

// 폼 유효성 검사
const isFormValid = computed(() => {
  return emoticonName.value.trim() !== '' && 
         thumbnailFile.value !== null &&
         emoticonPreviews.value.length > 0
})

// 등록 처리
const handleSubmit = async () => {
  if (!isFormValid.value || isSubmitting.value) return

  isSubmitting.value = true
  const currentRunId = ++submitRunId

  try {
    // 1. 썸네일 업로드
    const submitSnapshot = {
      thumbnail: thumbnailFile.value!,
      previews: [...emoticonPreviews.value],
      name: emoticonName.value.trim(),
      tags: [...tags.value],
    }

    // 2. 이모티콘 이미지 업로드 (리사이징 적용)
    uploadProgress.value = { current: 0, total: submitSnapshot.previews.length }

    let uploadFailed = false
    let submitFailure: unknown = null
    const failSubmit = (error: unknown) => {
      submitFailure ??= error
      uploadFailed = true
      abortPendingUploads()
    }

    const uploadThumbnail = async () => {
      assertSubmitActive(currentRunId)
      const controller = createUploadController()

      try {
        const response = await fileApi.uploadFile(submitSnapshot.thumbnail, {
          signal: controller.signal,
          skipGlobalErrorHandler: true
        })
        assertSubmitActive(currentRunId)
        return response.data.data.fileId
      } catch (error) {
        failSubmit(error)
        throw error
      } finally {
        uploadControllers.delete(controller)
      }
    }

    const uploadImages = async () => uploadEmoticonImagePreviews(
      submitSnapshot.previews,
      async (uploadFile) => {
        if (uploadFailed) {
          throw createUploadCancelledError()
        }

        assertSubmitActive(currentRunId)
        const controller = createUploadController()

        try {
          const response = await fileApi.uploadFile(uploadFile, {
            signal: controller.signal,
            skipGlobalErrorHandler: true
          })
          assertSubmitActive(currentRunId)
          return response.data.data.fileId
        } catch (error) {
          failSubmit(error)
          throw error
        } finally {
          uploadControllers.delete(controller)
        }
      },
      (current) => {
        if (!isComponentUnmounted && submitRunId === currentRunId) {
          uploadProgress.value.current = current
        }
      }
    )

    const [thumbnailFileId, imageFileIds] = await Promise.all([
      uploadThumbnail(),
      uploadImages().catch((error) => {
        failSubmit(error)
        throw error
      })
    ]).catch((error) => {
      throw submitFailure ?? error
    })
    assertSubmitActive(currentRunId)

    // 3. 이모티콘 생성
    await emoticonApi.createEmoticon({
      name: submitSnapshot.name,
      thumbnailFileId,
      tags: submitSnapshot.tags,
      imageFileIds
    })
    assertSubmitActive(currentRunId)

    toastStore.addToast(t('emoticon.register.created'), 'success')
    router.push({ name: 'emoticon-list' })
  } catch (error: unknown) {
    if (!isComponentUnmounted && !isUploadCancelledError(error)) {
      const message = extractErrorMessage(error) || t('emoticon.register.failed')
      toastStore.addToast(message, 'error')
    }
  } finally {
    if (!isComponentUnmounted && submitRunId === currentRunId) {
      isSubmitting.value = false
      submitRunId += 1
      uploadProgress.value = { current: 0, total: 0 }
    }
  }
}

// 목록으로 이동
const goToList = () => {
  router.push({ name: 'emoticon-list' })
}
</script>

<template>
  <div class="max-w-4xl mx-auto py-8 px-4 sm:px-6 lg:px-8">
    <!-- 페이지 제목과 목록으로 버튼 -->
    <div class="mb-8 flex items-start justify-between">
      <div>
        <h1 class="text-2xl font-bold text-gray-900 dark:text-white">노비콘 등록</h1>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">나만의 노비콘을 등록해보세요!</p>
      </div>
      <button
        @click="goToList"
        class="inline-flex items-center text-sm text-gray-600 dark:text-gray-400 hover:text-indigo-600 dark:hover:text-indigo-400 transition-colors"
      >
        <ArrowLeft class="w-4 h-4 mr-1" />
        목록으로
      </button>
    </div>

    <form @submit.prevent="handleSubmit" class="space-y-8">
      <!-- 이모티콘 이름과 썸네일 -->
      <div class="bg-white dark:bg-gray-800 rounded-lg shadow-sm border border-gray-200 dark:border-gray-700 p-6">
        <div class="flex flex-col md:flex-row gap-6">
          <!-- 썸네일 업로드 -->
          <div class="order-2 md:order-1 shrink-0">
            <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
              썸네일 이미지 <span class="text-red-500">*</span>
            </label>
            <p class="text-xs text-gray-500 dark:text-gray-400 mb-4">대표 이미지로 노비콘 목록에 표시됩니다. 500x500px 이하의 이미지만 업로드 가능합니다.</p>
            
            <div v-if="thumbnailPreview" class="relative inline-block">
              <img
                :src="thumbnailPreview"
                alt="썸네일 미리보기"
                class="w-32 h-32 object-contain bg-gray-100 dark:bg-gray-700 rounded-lg"
              />
              <button
                type="button"
                @click="removeThumbnail"
                class="absolute -top-2 -right-2 w-6 h-6 bg-red-500 text-white rounded-full flex items-center justify-center hover:bg-red-600"
              >
                <X class="w-4 h-4" />
              </button>
            </div>
            <div v-else>
              <input
                ref="thumbnailInput"
                type="file"
                :accept="SUPPORTED_IMAGE_ACCEPT"
                @change="handleThumbnailSelect"
                class="hidden"
              />
              <button
                type="button"
                @click="thumbnailInput?.click()"
                class="w-32 h-32 border-2 border-dashed border-gray-300 dark:border-gray-600 rounded-lg flex flex-col items-center justify-center text-gray-500 dark:text-gray-400 hover:border-indigo-500 hover:text-indigo-500 transition-colors"
              >
                <Upload class="w-8 h-8 mb-2" />
                <span class="text-xs">이미지 선택</span>
              </button>
            </div>
          </div>

          <!-- 이모티콘 이름 -->
          <div class="order-1 md:order-2 flex-1">
            <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
              이모티콘 이름 <span class="text-red-500">*</span>
            </label>
            <input
              v-model="emoticonName"
              type="text"
              maxlength="100"
              placeholder="이모티콘 이름을 입력하세요"
              class="w-full px-4 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white placeholder-gray-500 focus:ring-2 focus:ring-indigo-500 focus:border-transparent"
            />
          </div>
        </div>
      </div>

      <!-- 이모티콘 이미지 업로드 -->
      <div class="bg-white dark:bg-gray-800 rounded-lg shadow-sm border border-gray-200 dark:border-gray-700 p-6">
        <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
          이모티콘 이미지 <span class="text-red-500">*</span>
          <span class="text-xs font-normal text-gray-500 ml-2">({{ emoticonPreviews.length }}/100개)</span>
        </label>
        <p class="text-xs text-gray-500 dark:text-gray-400 mb-4">
          최대 100개까지 업로드 가능합니다. 500x500px 이하의 이미지만 업로드 가능하며, 100px 초과 시 자동으로 리사이징됩니다.
        </p>

        <!-- 이미지 그리드 -->
        <div class="grid grid-cols-5 sm:grid-cols-8 md:grid-cols-10 gap-2 mb-4">
          <div
            v-for="(item, index) in emoticonPreviews"
            :key="item.clientId"
            class="relative"
          >
            <img
              :src="item.preview"
              :alt="`이모티콘 ${index + 1}`"
              class="w-full aspect-square object-contain bg-gray-100 dark:bg-gray-700 rounded"
              style="width: 100px; height: 100px;"
            />
            <button
              type="button"
              @click="removeEmoticonImage(item.clientId)"
              class="absolute -top-1 -right-1 w-5 h-5 bg-red-500 text-white rounded-full flex items-center justify-center hover:bg-red-600 text-xs"
            >
              <X class="w-3 h-3" />
            </button>
          </div>

          <!-- 추가 버튼 -->
          <div v-if="emoticonPreviews.length < 100">
            <input
              ref="emoticonInput"
              type="file"
              :accept="SUPPORTED_IMAGE_ACCEPT"
              multiple
              @change="handleEmoticonSelect"
              class="hidden"
            />
            <button
              type="button"
              @click="emoticonInput?.click()"
              class="w-full aspect-square border-2 border-dashed border-gray-300 dark:border-gray-600 rounded flex flex-col items-center justify-center text-gray-500 dark:text-gray-400 hover:border-indigo-500 hover:text-indigo-500 transition-colors"
              style="width: 100px; height: 100px;"
            >
              <Plus class="w-6 h-6" />
            </button>
          </div>
        </div>
      </div>

      <!-- 태그 입력 -->
      <div class="bg-white dark:bg-gray-800 rounded-lg shadow-sm border border-gray-200 dark:border-gray-700 p-6">
        <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
          태그
          <span class="text-xs font-normal text-gray-500 ml-2">({{ tags.length }}/10개)</span>
        </label>
        <p class="text-xs text-gray-500 dark:text-gray-400 mb-4">검색에 사용될 태그를 입력하세요.</p>

        <div class="flex gap-2 mb-4">
          <input
            v-model="tagInput"
            @keydown.enter.prevent="addTag"
            type="text"
            placeholder="태그 입력 후 Enter"
            class="flex-1 px-4 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white placeholder-gray-500 focus:ring-2 focus:ring-indigo-500 focus:border-transparent"
          />
          <BaseButton type="button" @click="addTag" variant="secondary">
            추가
          </BaseButton>
        </div>

        <div v-if="tags.length > 0" class="flex flex-wrap gap-2">
          <span
            v-for="tagItem in tagItems"
            :key="tagItem.clientId"
            class="inline-flex items-center px-3 py-1 rounded-full text-sm font-medium bg-indigo-100 text-indigo-800 dark:bg-indigo-900/30 dark:text-indigo-300"
          >
            #{{ tagItem.value }}
            <button
              type="button"
              @click="removeTag(tagItem.clientId)"
              class="ml-1 text-indigo-600 hover:text-indigo-800 dark:text-indigo-400 dark:hover:text-indigo-200"
            >
              <X class="w-3 h-3" />
            </button>
          </span>
        </div>
      </div>

      <!-- 등록 버튼 -->
      <div class="flex flex-col items-end gap-2">
        <div v-if="isSubmitting && uploadProgress.total > 0" class="text-sm text-gray-600 dark:text-gray-400">
          이미지 업로드 중... ({{ uploadProgress.current }}/{{ uploadProgress.total }})
        </div>
        <BaseButton
          type="submit"
          :disabled="!isFormValid || isSubmitting"
          variant="primary"
          size="lg"
        >
          {{ isSubmitting ? '등록 중...' : '등록하기' }}
        </BaseButton>
      </div>
    </form>
  </div>
</template>

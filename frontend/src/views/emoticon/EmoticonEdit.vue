<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useQuery, useQueryClient } from '@tanstack/vue-query'
import { emoticonApi } from '@/api/emoticon'
import { fileApi } from '@/api/file'
import { useHead } from '@unhead/vue'
import { ArrowLeft, Upload, X, Plus, EyeOff, Eye } from 'lucide-vue-next'
import { useToastStore } from '@/stores/toast'
import { useAuthStore } from '@/stores/auth'
import { useI18n } from 'vue-i18n'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import type { EmoticonImage } from '@/types/emoticon'
import { extractErrorMessage } from '@/utils/errorHandler'
import { useEmoticonImageSelection } from '@/composables/useEmoticonImageSelection'
import { useToggleEmoticonVisibility } from '@/composables/useToggleEmoticonVisibility'
import {
  createUploadableEmoticonImageFile,
  resolveEmoticonTagAddition,
  revokeEmoticonPreviewUrl,
  SUPPORTED_EMOTICON_IMAGE_ACCEPT,
  type EmoticonImagePreview
} from '@/utils/emoticonImage'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const toastStore = useToastStore()
const authStore = useAuthStore()
const queryClient = useQueryClient()
const { selectThumbnailImage, selectEmoticonImages } = useEmoticonImageSelection(t, toastStore)

const emoticonId = computed(() => Number(route.params.emoticonId))

useHead({
  title: computed(() => emoticon.value?.name ? `${emoticon.value.name} 수정 - 노비콘` : '노비콘 수정')
})

// 기존 이모티콘 데이터 조회
const { data: emoticon, isLoading } = useQuery({
  queryKey: ['emoticon', emoticonId],
  queryFn: async () => {
    return emoticonApi.getEmoticonData(emoticonId.value)
  },
  enabled: () => !!emoticonId.value
})

// 폼 상태
const emoticonName = ref('')
const thumbnailFile = ref<File | null>(null)
const thumbnailPreview = ref<string | null>(null)
const originalThumbnailUrl = ref<string | null>(null)
const newEmoticonPreviews = ref<EmoticonImagePreview[]>([])
const existingImages = ref<EmoticonImage[]>([])
const imagesToDelete = ref<number[]>([])
const tagInput = ref('')
const isSubmitting = ref(false)
const uploadProgress = ref({ current: 0, total: 0 })
const uploadControllers = new Set<AbortController>()
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

const createUploadCancelledError = () => new DOMException('Upload has been cancelled', 'AbortError')

const assertSubmitActive = () => {
  if (isComponentUnmounted) {
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

// 권한 체크
const isOwner = computed(() => {
  if (!emoticon.value || !authStore.user) return false
  return emoticon.value.creatorId === authStore.user.userId
})

// 기존 데이터로 폼 초기화
watch(emoticon, (data) => {
  if (data) {
    emoticonName.value = data.name || ''
    tags.value = [...(data.tags || [])]
    existingImages.value = [...(data.images || [])]
    originalThumbnailUrl.value = data.thumbnailUrl || null
    thumbnailPreview.value = data.thumbnailUrl || null
  }
}, { immediate: true })

// 권한 없으면 목록으로 리다이렉트
onMounted(() => {
  if (!authStore.isAuthenticated) {
    router.push({ name: 'emoticon-list' })
  }
})

watch([emoticon, () => authStore.user], ([emoticonData, user]) => {
  if (emoticonData && user && emoticonData.creatorId !== user.userId) {
    toastStore.addToast(t('emoticon.edit.noPermission'), 'error')
    router.push({ name: 'emoticon-detail', params: { emoticonId: emoticonId.value } })
  }
})

// 숨김/표시 전환
const { mutate: toggleVisibility, isPending: isToggling } = useToggleEmoticonVisibility(emoticonId)

const handleToggleVisibility = () => {
  if (!emoticon.value) return
  const verb = emoticon.value.isActive ? t('emoticon.visibility.hideConfirm') : t('emoticon.visibility.showConfirm')
  if (confirm(verb)) {
    toggleVisibility()
  }
}

const SUPPORTED_IMAGE_ACCEPT = SUPPORTED_EMOTICON_IMAGE_ACCEPT

onUnmounted(() => {
  isComponentUnmounted = true
  abortPendingUploads()
  revokeEmoticonPreviewUrl(thumbnailPreview.value)
  newEmoticonPreviews.value.forEach((item) => {
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

// 썸네일 변경 (변경할 수 있도록)
const changeThumbnail = () => {
  thumbnailInput.value?.click()
}

// 이모티콘 이미지 선택 (새로 추가할 이미지)
const handleEmoticonSelect = async (event: Event) => {
  const input = event.target as HTMLInputElement
  const files = input.files
  if (!files) return

  const currentCount = existingImages.value.filter(img => !imagesToDelete.value.includes(img.imageId)).length + newEmoticonPreviews.value.length
  const remainingSlots = 100 - currentCount
  const selectedImages = await selectEmoticonImages(files, remainingSlots)
  newEmoticonPreviews.value.push(...selectedImages)

  // 입력 초기화
  if (emoticonInput.value) {
    emoticonInput.value.value = ''
  }
}

// 기존 이미지 삭제 표시
const markImageForDeletion = (imageId: number) => {
  if (!imagesToDelete.value.includes(imageId)) {
    imagesToDelete.value.push(imageId)
  }
}

// 삭제 표시 취소
const unmarkImageForDeletion = (imageId: number) => {
  const index = imagesToDelete.value.indexOf(imageId)
  if (index > -1) {
    imagesToDelete.value.splice(index, 1)
  }
}

// 새 이미지 제거
const removeNewEmoticonImage = (clientId: string) => {
  const index = newEmoticonPreviews.value.findIndex((item) => item.clientId === clientId)
  const item = index >= 0 ? newEmoticonPreviews.value[index] : null
  if (item) {
    revokeEmoticonPreviewUrl(item.preview)
    newEmoticonPreviews.value.splice(index, 1)
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

// 총 이미지 개수 계산
const totalImageCount = computed(() => {
  const existingCount = existingImages.value.filter(img => !imagesToDelete.value.includes(img.imageId)).length
  return existingCount + newEmoticonPreviews.value.length
})

// 폼 유효성 검사
const isFormValid = computed(() => {
  return emoticonName.value.trim() !== '' &&
    thumbnailPreview.value !== null &&
    totalImageCount.value > 0
})

// 수정 처리
const handleSubmit = async () => {
  if (!isFormValid.value || isSubmitting.value) return

  isSubmitting.value = true

  try {
    const submitSnapshot = {
      thumbnail: thumbnailFile.value,
      imagesToDelete: [...imagesToDelete.value],
      newPreviews: [...newEmoticonPreviews.value],
      name: emoticonName.value.trim(),
      tags: [...tags.value],
    }
    const uploadFiles = submitSnapshot.newPreviews.length > 0
      ? await Promise.all(submitSnapshot.newPreviews.map((item) => createUploadableEmoticonImageFile(item)))
      : []
    assertSubmitActive()

    // 1. 썸네일 업로드 (변경된 경우에만)
    let thumbnailFileId: number | undefined
    if (submitSnapshot.thumbnail) {
      const controller = createUploadController()

      try {
        const thumbnailResponse = await fileApi.uploadFile(submitSnapshot.thumbnail, { signal: controller.signal })
        assertSubmitActive()
        thumbnailFileId = thumbnailResponse.data.data.fileId
      } finally {
        uploadControllers.delete(controller)
      }
    }

    // 2. 기존 이미지 삭제 처리
    await Promise.all(submitSnapshot.imagesToDelete.map(async (imageId) => {
      await emoticonApi.deleteImage(imageId)
      assertSubmitActive()
    }))

    // 3. 새 이미지 업로드 및 추가
    if (uploadFiles.length > 0) {
      uploadProgress.value = { current: 0, total: uploadFiles.length }

      let uploadFailed = false

      const imageFileIds = await (async () => {
        try {
          let completed = 0

          return await Promise.all(
            uploadFiles.map(async (uploadFile) => {
              if (uploadFailed) {
                throw createUploadCancelledError()
              }

              assertSubmitActive()
              const controller = createUploadController()

              try {
                const response = await fileApi.uploadFile(uploadFile, { signal: controller.signal })
                assertSubmitActive()
                completed += 1
                if (!isComponentUnmounted) {
                  uploadProgress.value.current = completed
                }
                return response.data.data.fileId
              } catch (error) {
                uploadFailed = true
                abortPendingUploads()
                throw error
              } finally {
                uploadControllers.delete(controller)
              }
            })
          )
        } catch (error) {
          uploadFailed = true
          abortPendingUploads()
          throw error
        }
      })()

      for (const fileId of imageFileIds) {
        assertSubmitActive()
        await emoticonApi.addImage(emoticonId.value, fileId)
        assertSubmitActive()
      }
    }

    // 4. 이모티콘 정보 수정 (이름, 썸네일, 태그)
    assertSubmitActive()
    await emoticonApi.updateEmoticon(emoticonId.value, {
      name: submitSnapshot.name,
      thumbnailFileId,
      tags: submitSnapshot.tags
    })
    assertSubmitActive()

    // 캐시 무효화
    queryClient.invalidateQueries({ queryKey: ['emoticon', emoticonId] })
    queryClient.invalidateQueries({ queryKey: ['emoticons'] })

    toastStore.addToast(t('emoticon.edit.updated'), 'success')
    router.push({ name: 'emoticon-detail', params: { emoticonId: emoticonId.value } })
  } catch (error: unknown) {
    if (!isComponentUnmounted) {
      const message = extractErrorMessage(error) || t('emoticon.edit.failed')
      toastStore.addToast(message, 'error')
    }
  } finally {
    if (!isComponentUnmounted) {
      isSubmitting.value = false
      uploadProgress.value = { current: 0, total: 0 }
    }
  }
}

// 상세 페이지로 이동
const goToDetail = () => {
  router.push({ name: 'emoticon-detail', params: { emoticonId: emoticonId.value } })
}
</script>

<template>
  <div class="max-w-4xl mx-auto py-8 px-4 sm:px-6 lg:px-8">
    <!-- 페이지 제목과 뒤로가기 버튼 -->
    <div class="mb-8 flex items-start justify-between">
      <div>
        <h1 class="text-2xl font-bold text-gray-900 dark:text-white">노비콘 수정</h1>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">노비콘 정보를 수정합니다.</p>
      </div>
      <button @click="goToDetail"
        class="inline-flex items-center text-sm text-gray-600 dark:text-gray-400 hover:text-indigo-600 dark:hover:text-indigo-400 transition-colors">
        <ArrowLeft class="w-4 h-4 mr-1" />
        뒤로
      </button>
    </div>

    <!-- 로딩 -->
    <div v-if="isLoading" class="animate-pulse space-y-8">
      <div class="bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700 p-6">
        <div class="flex gap-6">
          <div class="w-32 h-32 bg-gray-200 dark:bg-gray-700 rounded-lg"></div>
          <div class="flex-1">
            <div class="h-10 bg-gray-200 dark:bg-gray-700 rounded w-full"></div>
          </div>
        </div>
      </div>
      <div class="bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700 p-6">
        <div class="grid grid-cols-5 gap-2">
          <div v-for="i in 5" :key="i" class="aspect-square bg-gray-200 dark:bg-gray-700 rounded"></div>
        </div>
      </div>
    </div>

    <!-- 권한 없음 -->
    <div v-else-if="emoticon && !isOwner" class="text-center py-20">
      <p class="text-red-500 dark:text-red-400">수정 권한이 없습니다.</p>
    </div>

    <!-- 폼 -->
    <form v-else-if="emoticon" @submit.prevent="handleSubmit" class="space-y-8">
      <!-- 숨김/표시 전환 (등록자만) -->
      <div class="bg-white dark:bg-gray-800 rounded-lg shadow-sm border border-gray-200 dark:border-gray-700 p-4">
        <div class="flex items-center justify-between">
          <div>
            <span v-if="!emoticon.isActive"
              class="inline-flex items-center px-2.5 py-0.5 rounded text-xs font-medium bg-gray-200 text-gray-700 dark:bg-gray-600 dark:text-gray-300">
              {{ $t('emoticon.visibility.hidden') }}
            </span>
            <span v-else class="text-sm text-gray-500 dark:text-gray-400">현재 판매 중입니다</span>
          </div>
          <button type="button" @click="handleToggleVisibility" :disabled="isToggling"
            :class="emoticon.isActive
              ? 'inline-flex items-center px-3 py-1.5 text-sm text-amber-700 dark:text-amber-300 hover:bg-amber-50 dark:hover:bg-amber-900/20 rounded-lg transition-colors'
              : 'inline-flex items-center px-3 py-1.5 text-sm text-green-700 dark:text-green-300 hover:bg-green-50 dark:hover:bg-green-900/20 rounded-lg transition-colors'">
            <EyeOff v-if="emoticon.isActive" class="w-4 h-4 mr-1" />
            <Eye v-else class="w-4 h-4 mr-1" />
            {{ emoticon.isActive ? $t('emoticon.visibility.hide') : $t('emoticon.visibility.show') }}
          </button>
        </div>
      </div>

      <!-- 이모티콘 이름과 썸네일 -->
      <div class="bg-white dark:bg-gray-800 rounded-lg shadow-sm border border-gray-200 dark:border-gray-700 p-6">
        <div class="flex flex-col md:flex-row gap-6">
          <!-- 썸네일 -->
          <div class="order-2 md:order-1 shrink-0">
            <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
              썸네일 이미지 <span class="text-red-500">*</span>
            </label>
            <p class="text-xs text-gray-500 dark:text-gray-400 mb-4">대표 이미지로 노비콘 목록에 표시됩니다. 500x500px 이하의 이미지만 업로드
              가능합니다.</p>

            <div class="relative inline-block">
              <img v-if="thumbnailPreview" :src="thumbnailPreview" alt="썸네일 미리보기"
                class="w-32 h-32 object-contain bg-gray-100 dark:bg-gray-700 rounded-lg cursor-pointer hover:opacity-80 transition-opacity"
                @click="changeThumbnail" title="클릭하여 이미지 변경" />
              <div v-else
                class="w-32 h-32 bg-gray-100 dark:bg-gray-700 rounded-lg flex items-center justify-center text-gray-400">
                No Image
              </div>
              <input ref="thumbnailInput" type="file" :accept="SUPPORTED_IMAGE_ACCEPT" @change="handleThumbnailSelect" class="hidden" />
              <button type="button" @click="changeThumbnail"
                class="absolute -bottom-2 -right-2 w-8 h-8 bg-indigo-500 text-white rounded-full flex items-center justify-center hover:bg-indigo-600 shadow-md"
                title="썸네일 변경">
                <Upload class="w-4 h-4" />
              </button>
            </div>
          </div>

          <!-- 이모티콘 이름 -->
          <div class="order-1 md:order-2 flex-1">
            <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
              이모티콘 이름 <span class="text-red-500">*</span>
            </label>
            <input v-model="emoticonName" type="text" maxlength="100" placeholder="이모티콘 이름을 입력하세요"
              class="w-full px-4 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white placeholder-gray-500 focus:ring-2 focus:ring-indigo-500 focus:border-transparent" />
          </div>
        </div>
      </div>

      <!-- 이모티콘 이미지 -->
      <div class="bg-white dark:bg-gray-800 rounded-lg shadow-sm border border-gray-200 dark:border-gray-700 p-6">
        <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
          이모티콘 이미지 <span class="text-red-500">*</span>
          <span class="text-xs font-normal text-gray-500 ml-2">({{ totalImageCount }}/100개)</span>
        </label>
        <p class="text-xs text-gray-500 dark:text-gray-400 mb-4">
          최대 100개까지 업로드 가능합니다. 500x500px 이하의 이미지만 업로드 가능하며, 100px 초과 시 자동으로 리사이징됩니다.
        </p>

        <!-- 이미지 그리드 -->
        <div class="grid grid-cols-5 sm:grid-cols-8 md:grid-cols-10 gap-2 mb-4">
          <!-- 기존 이미지 -->
          <div v-for="image in existingImages" :key="'existing-' + image.imageId" class="relative"
            :class="{ 'opacity-40': imagesToDelete.includes(image.imageId) }">
            <img :src="image.imageUrl" :alt="`이모티콘 ${image.sortOrder + 1}`"
              class="w-full aspect-square object-contain bg-gray-100 dark:bg-gray-700 rounded"
              style="width: 100px; height: 100px;" />
            <button v-if="!imagesToDelete.includes(image.imageId)" type="button"
              @click="markImageForDeletion(image.imageId)"
              class="absolute -top-1 -right-1 w-5 h-5 bg-red-500 text-white rounded-full flex items-center justify-center hover:bg-red-600 text-xs"
              title="삭제">
              <X class="w-3 h-3" />
            </button>
            <button v-else type="button" @click="unmarkImageForDeletion(image.imageId)"
              class="absolute -top-1 -right-1 w-5 h-5 bg-gray-500 text-white rounded-full flex items-center justify-center hover:bg-gray-600 text-xs"
              title="삭제 취소">
              <Plus class="w-3 h-3" />
            </button>
          </div>

          <!-- 새로 추가할 이미지 -->
          <div v-for="(item, index) in newEmoticonPreviews" :key="item.clientId" class="relative">
            <img :src="item.preview" :alt="`새 이모티콘 ${index + 1}`"
              class="w-full aspect-square object-contain bg-green-50 dark:bg-green-900/20 rounded border-2 border-green-400"
              style="width: 100px; height: 100px;" />
            <button type="button" @click="removeNewEmoticonImage(item.clientId)"
              class="absolute -top-1 -right-1 w-5 h-5 bg-red-500 text-white rounded-full flex items-center justify-center hover:bg-red-600 text-xs">
              <X class="w-3 h-3" />
            </button>
          </div>

          <!-- 추가 버튼 -->
          <div v-if="totalImageCount < 100">
            <input ref="emoticonInput" type="file" :accept="SUPPORTED_IMAGE_ACCEPT" multiple @change="handleEmoticonSelect"
              class="hidden" />
            <button type="button" @click="emoticonInput?.click()"
              class="w-full aspect-square border-2 border-dashed border-gray-300 dark:border-gray-600 rounded flex flex-col items-center justify-center text-gray-500 dark:text-gray-400 hover:border-indigo-500 hover:text-indigo-500 transition-colors"
              style="width: 100px; height: 100px;">
              <Plus class="w-6 h-6" />
            </button>
          </div>
        </div>

        <!-- 변경 안내 -->
        <div v-if="imagesToDelete.length > 0 || newEmoticonPreviews.length > 0"
          class="text-xs text-gray-500 dark:text-gray-400 mt-2">
          <span v-if="imagesToDelete.length > 0" class="text-red-500">{{ imagesToDelete.length }}개 삭제 예정</span>
          <span v-if="imagesToDelete.length > 0 && newEmoticonPreviews.length > 0"> · </span>
          <span v-if="newEmoticonPreviews.length > 0" class="text-green-500">{{ newEmoticonPreviews.length }}개 추가
            예정</span>
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
          <input v-model="tagInput" @keydown.enter.prevent="addTag" type="text" placeholder="태그 입력 후 Enter"
            class="flex-1 px-4 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white placeholder-gray-500 focus:ring-2 focus:ring-indigo-500 focus:border-transparent" />
          <BaseButton type="button" @click="addTag" variant="secondary">
            추가
          </BaseButton>
        </div>

        <div v-if="tags.length > 0" class="flex flex-wrap gap-2">
          <span v-for="tagItem in tagItems" :key="tagItem.clientId"
            class="inline-flex items-center px-3 py-1 rounded-full text-sm font-medium bg-indigo-100 text-indigo-800 dark:bg-indigo-900/30 dark:text-indigo-300">
            #{{ tagItem.value }}
            <button type="button" @click="removeTag(tagItem.clientId)"
              class="ml-1 text-indigo-600 hover:text-indigo-800 dark:text-indigo-400 dark:hover:text-indigo-200">
              <X class="w-3 h-3" />
            </button>
          </span>
        </div>
      </div>

      <!-- 수정 버튼 -->
      <div class="flex flex-col items-end gap-2">
        <div v-if="isSubmitting && uploadProgress.total > 0" class="text-sm text-gray-600 dark:text-gray-400">
          이미지 업로드 중... ({{ uploadProgress.current }}/{{ uploadProgress.total }})
        </div>
        <div class="flex gap-3">
          <BaseButton type="button" @click="goToDetail" variant="secondary" size="lg">
            취소
          </BaseButton>
          <BaseButton type="submit" :disabled="!isFormValid || isSubmitting" variant="primary" size="lg">
            {{ isSubmitting ? '수정 중...' : '수정하기' }}
          </BaseButton>
        </div>
      </div>
    </form>
  </div>
</template>

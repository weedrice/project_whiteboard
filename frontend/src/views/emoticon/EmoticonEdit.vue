<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useQuery, useMutation, useQueryClient } from '@tanstack/vue-query'
import { emoticonApi } from '@/api/emoticon'
import { fileApi } from '@/api/file'
import { useHead } from '@unhead/vue'
import { ArrowLeft, Upload, X, Plus } from 'lucide-vue-next'
import { useToastStore } from '@/stores/toast'
import { useAuthStore } from '@/stores/auth'
import { useI18n } from 'vue-i18n'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import type { EmoticonImage } from '@/types/emoticon'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const toastStore = useToastStore()
const authStore = useAuthStore()
const queryClient = useQueryClient()

const emoticonId = computed(() => Number(route.params.emoticonId))

useHead({
  title: computed(() => emoticon.value?.name ? `${emoticon.value.name} 수정 - 노비콘` : '노비콘 수정')
})

// 기존 이모티콘 데이터 조회
const { data: emoticon, isLoading } = useQuery({
  queryKey: ['emoticon', emoticonId],
  queryFn: async () => {
    const { data } = await emoticonApi.getEmoticon(emoticonId.value)
    return data.data
  },
  enabled: () => !!emoticonId.value
})

// 폼 상태
const emoticonName = ref('')
const thumbnailFile = ref<File | null>(null)
const thumbnailPreview = ref<string | null>(null)
const originalThumbnailUrl = ref<string | null>(null)
const newEmoticonPreviews = ref<{ file: File; preview: string; width: number; height: number }[]>([])
const existingImages = ref<EmoticonImage[]>([])
const imagesToDelete = ref<number[]>([])
const tagInput = ref('')
const tags = ref<string[]>([])
const isSubmitting = ref(false)
const uploadProgress = ref({ current: 0, total: 0 })

// 파일 입력 refs
const thumbnailInput = ref<HTMLInputElement | null>(null)
const emoticonInput = ref<HTMLInputElement | null>(null)

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

// 이미지 리사이징 함수 (100px 이하로)
const resizeImage = (file: File, maxSize: number = 100): Promise<Blob> => {
  return new Promise((resolve, reject) => {
    const img = new Image()
    const reader = new FileReader()

    reader.onload = (e) => {
      img.src = e.target?.result as string
    }

    img.onload = () => {
      let { width, height } = img
      
      // 이미 충분히 작으면 그대로 반환 (Blob로 변환)
      if (width <= maxSize && height <= maxSize) {
        file.arrayBuffer().then(buffer => {
          resolve(new Blob([buffer], { type: file.type || 'image/png' }))
        }).catch(reject)
        return
      }

      // 비율 유지하며 긴 쪽이 maxSize가 되도록 리사이징
      if (width > height) {
        height = Math.round((height * maxSize) / width)
        width = maxSize
      } else {
        width = Math.round((width * maxSize) / height)
        height = maxSize
      }

      const canvas = document.createElement('canvas')
      canvas.width = width
      canvas.height = height
      const ctx = canvas.getContext('2d')
      
      if (!ctx) {
        reject(new Error('Canvas context not available'))
        return
      }

      ctx.drawImage(img, 0, 0, width, height)
      
      canvas.toBlob(
        (blob) => {
          if (blob) {
            resolve(blob)
          } else {
            reject(new Error('Failed to create blob'))
          }
        },
        file.type || 'image/png',
        0.9
      )
    }

    img.onerror = () => reject(new Error('Failed to load image'))
    reader.onerror = () => reject(new Error('Failed to read file'))
    reader.readAsDataURL(file)
  })
}

// 썸네일 선택
const handleThumbnailSelect = async (event: Event) => {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return

  // 이미지 파일 검증
  if (!file.type.startsWith('image/')) {
    toastStore.addToast(t('emoticon.validation.imageOnly'), 'error')
    return
  }

  // 500x500px 제한 확인
  const img = new Image()
  const preview = URL.createObjectURL(file)
  
  await new Promise<void>((resolve) => {
    img.onload = () => {
      if (img.width > 500 || img.height > 500) {
        toastStore.addToast(t('emoticon.validation.imageSizeExceeded', { width: img.width, height: img.height }), 'error')
        URL.revokeObjectURL(preview)
      } else {
        thumbnailFile.value = file
        thumbnailPreview.value = preview
      }
      resolve()
    }
    img.onerror = () => {
      toastStore.addToast(t('emoticon.validation.imageLoadFailed'), 'error')
      URL.revokeObjectURL(preview)
      resolve()
    }
    img.src = preview
  })
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
  if (remainingSlots <= 0) {
    toastStore.addToast(t('emoticon.validation.maxImages'), 'error')
    return
  }

  const filesToAdd = Array.from(files).slice(0, remainingSlots)
  
  for (const file of filesToAdd) {
    // 이미지 파일 검증
    if (!file.type.startsWith('image/')) {
      toastStore.addToast(t('emoticon.validation.notImage', { name: file.name }), 'error')
      continue
    }

    // 500x500px 제한 확인 및 크기 정보 저장
    const img = new Image()
    const preview = URL.createObjectURL(file)
    
    await new Promise<void>((resolve) => {
      img.onload = () => {
        if (img.width > 500 || img.height > 500) {
          toastStore.addToast(t('emoticon.validation.imageSizeExceededNamed', { name: file.name, width: img.width, height: img.height }), 'error')
          URL.revokeObjectURL(preview)
        } else {
          newEmoticonPreviews.value.push({ 
            file, 
            preview, 
            width: img.width, 
            height: img.height 
          })
        }
        resolve()
      }
      img.onerror = () => {
        toastStore.addToast(t('emoticon.validation.loadFailedNamed', { name: file.name }), 'error')
        URL.revokeObjectURL(preview)
        resolve()
      }
      img.src = preview
    })
  }

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
const removeNewEmoticonImage = (index: number) => {
  const item = newEmoticonPreviews.value[index]
  if (item) {
    URL.revokeObjectURL(item.preview)
    newEmoticonPreviews.value.splice(index, 1)
  }
}

// 태그 추가
const addTag = () => {
  const tag = tagInput.value.trim().replace(/^#/, '')
  if (tag && !tags.value.includes(tag)) {
    if (tags.value.length >= 10) {
      toastStore.addToast(t('emoticon.validation.maxTags'), 'error')
      return
    }
    tags.value.push(tag)
  }
  tagInput.value = ''
}

// 태그 제거
const removeTag = (index: number) => {
  tags.value.splice(index, 1)
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
    // 1. 썸네일 업로드 (변경된 경우에만)
    let thumbnailUrl = originalThumbnailUrl.value
    if (thumbnailFile.value) {
      const thumbnailResponse = await fileApi.uploadFile(thumbnailFile.value)
      thumbnailUrl = thumbnailResponse.data.data.url
    }

    // 2. 기존 이미지 삭제 처리
    for (const imageId of imagesToDelete.value) {
      await emoticonApi.deleteImage(imageId)
    }

    // 3. 새 이미지 업로드 및 추가
    if (newEmoticonPreviews.value.length > 0) {
      uploadProgress.value = { current: 0, total: newEmoticonPreviews.value.length }
      
      for (let i = 0; i < newEmoticonPreviews.value.length; i++) {
        const item = newEmoticonPreviews.value[i]
        
        // 저장된 크기 정보 사용 (이미지 재로드 불필요)
        let fileToUpload: File | Blob = item.file
        const needsResize = item.width > 100 || item.height > 100
        
        if (needsResize) {
          fileToUpload = await resizeImage(item.file, 100)
          // 리사이징 후 메모리 정리를 위해 작은 딜레이
          await new Promise(resolve => setTimeout(resolve, 50))
        }
        
        // File 객체로 변환 (Blob인 경우)
        const uploadFile = fileToUpload instanceof File 
          ? fileToUpload 
          : new File([fileToUpload], item.file.name, { type: item.file.type || 'image/png' })
        
        const response = await fileApi.uploadFile(uploadFile)
        await emoticonApi.addImage(emoticonId.value, response.data.data.url)
        
        // 진행 상태 업데이트
        uploadProgress.value.current = i + 1
        
        // UI가 블로킹되지 않도록 작은 딜레이 추가 (마지막 항목 제외)
        if (i < newEmoticonPreviews.value.length - 1) {
          await new Promise(resolve => setTimeout(resolve, 20))
        }
      }
    }

    // 4. 이모티콘 정보 수정 (이름, 썸네일, 태그)
    await emoticonApi.updateEmoticon(emoticonId.value, {
      name: emoticonName.value.trim(),
      thumbnailUrl: thumbnailUrl || undefined,
      tags: tags.value
    })

    // 캐시 무효화
    queryClient.invalidateQueries({ queryKey: ['emoticon', emoticonId] })
    queryClient.invalidateQueries({ queryKey: ['emoticons'] })

    toastStore.addToast(t('emoticon.edit.updated'), 'success')
    router.push({ name: 'emoticon-detail', params: { emoticonId: emoticonId.value } })
  } catch (error: any) {
    const message = error.response?.data?.error?.message || t('emoticon.edit.failed')
    toastStore.addToast(message, 'error')
  } finally {
    isSubmitting.value = false
    uploadProgress.value = { current: 0, total: 0 }
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
      <button
        @click="goToDetail"
        class="inline-flex items-center text-sm text-gray-600 dark:text-gray-400 hover:text-indigo-600 dark:hover:text-indigo-400 transition-colors"
      >
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
      <!-- 이모티콘 이름과 썸네일 -->
      <div class="bg-white dark:bg-gray-800 rounded-lg shadow-sm border border-gray-200 dark:border-gray-700 p-6">
        <div class="flex flex-col md:flex-row gap-6">
          <!-- 썸네일 -->
          <div class="order-2 md:order-1 shrink-0">
            <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
              썸네일 이미지 <span class="text-red-500">*</span>
            </label>
            <p class="text-xs text-gray-500 dark:text-gray-400 mb-4">대표 이미지로 노비콘 목록에 표시됩니다. 500x500px 이하의 이미지만 업로드 가능합니다.</p>
            
            <div class="relative inline-block">
              <img
                v-if="thumbnailPreview"
                :src="thumbnailPreview"
                alt="썸네일 미리보기"
                class="w-32 h-32 object-contain bg-gray-100 dark:bg-gray-700 rounded-lg cursor-pointer hover:opacity-80 transition-opacity"
                @click="changeThumbnail"
                title="클릭하여 이미지 변경"
              />
              <div v-else class="w-32 h-32 bg-gray-100 dark:bg-gray-700 rounded-lg flex items-center justify-center text-gray-400">
                No Image
              </div>
              <input
                ref="thumbnailInput"
                type="file"
                accept="image/*"
                @change="handleThumbnailSelect"
                class="hidden"
              />
              <button
                type="button"
                @click="changeThumbnail"
                class="absolute -bottom-2 -right-2 w-8 h-8 bg-indigo-500 text-white rounded-full flex items-center justify-center hover:bg-indigo-600 shadow-md"
                title="썸네일 변경"
              >
                <Upload class="w-4 h-4" />
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
          <div
            v-for="image in existingImages"
            :key="'existing-' + image.imageId"
            class="relative"
            :class="{ 'opacity-40': imagesToDelete.includes(image.imageId) }"
          >
            <img
              :src="image.imageUrl"
              :alt="`이모티콘 ${image.sortOrder + 1}`"
              class="w-full aspect-square object-contain bg-gray-100 dark:bg-gray-700 rounded"
              style="width: 100px; height: 100px;"
            />
            <button
              v-if="!imagesToDelete.includes(image.imageId)"
              type="button"
              @click="markImageForDeletion(image.imageId)"
              class="absolute -top-1 -right-1 w-5 h-5 bg-red-500 text-white rounded-full flex items-center justify-center hover:bg-red-600 text-xs"
              title="삭제"
            >
              <X class="w-3 h-3" />
            </button>
            <button
              v-else
              type="button"
              @click="unmarkImageForDeletion(image.imageId)"
              class="absolute -top-1 -right-1 w-5 h-5 bg-gray-500 text-white rounded-full flex items-center justify-center hover:bg-gray-600 text-xs"
              title="삭제 취소"
            >
              <Plus class="w-3 h-3" />
            </button>
          </div>

          <!-- 새로 추가할 이미지 -->
          <div
            v-for="(item, index) in newEmoticonPreviews"
            :key="'new-' + index"
            class="relative"
          >
            <img
              :src="item.preview"
              :alt="`새 이모티콘 ${index + 1}`"
              class="w-full aspect-square object-contain bg-green-50 dark:bg-green-900/20 rounded border-2 border-green-400"
              style="width: 100px; height: 100px;"
            />
            <button
              type="button"
              @click="removeNewEmoticonImage(index)"
              class="absolute -top-1 -right-1 w-5 h-5 bg-red-500 text-white rounded-full flex items-center justify-center hover:bg-red-600 text-xs"
            >
              <X class="w-3 h-3" />
            </button>
          </div>

          <!-- 추가 버튼 -->
          <div v-if="totalImageCount < 100">
            <input
              ref="emoticonInput"
              type="file"
              accept="image/*"
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

        <!-- 변경 안내 -->
        <div v-if="imagesToDelete.length > 0 || newEmoticonPreviews.length > 0" class="text-xs text-gray-500 dark:text-gray-400 mt-2">
          <span v-if="imagesToDelete.length > 0" class="text-red-500">{{ imagesToDelete.length }}개 삭제 예정</span>
          <span v-if="imagesToDelete.length > 0 && newEmoticonPreviews.length > 0"> · </span>
          <span v-if="newEmoticonPreviews.length > 0" class="text-green-500">{{ newEmoticonPreviews.length }}개 추가 예정</span>
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
            v-for="(tag, index) in tags"
            :key="index"
            class="inline-flex items-center px-3 py-1 rounded-full text-sm font-medium bg-indigo-100 text-indigo-800 dark:bg-indigo-900/30 dark:text-indigo-300"
          >
            #{{ tag }}
            <button
              type="button"
              @click="removeTag(index)"
              class="ml-1 text-indigo-600 hover:text-indigo-800 dark:text-indigo-400 dark:hover:text-indigo-200"
            >
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
          <BaseButton
            type="submit"
            :disabled="!isFormValid || isSubmitting"
            variant="primary"
            size="lg"
          >
            {{ isSubmitting ? '수정 중...' : '수정하기' }}
          </BaseButton>
        </div>
      </div>
    </form>
  </div>
</template>

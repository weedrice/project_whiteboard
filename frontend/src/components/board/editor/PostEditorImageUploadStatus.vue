<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import BaseSpinner from '@/components/common/ui/BaseSpinner.vue'
import { X } from 'lucide-vue-next'

defineProps<{
  isUploadingImage: boolean
  hasImageUploadError: boolean
  currentUploadingImageName: string
  imageUploadQueueCount: number
  failedImageCount: number
  failedImageFiles: File[]
}>()

const emit = defineEmits<{
  (e: 'retry-image-upload'): void
  (e: 'retry-failed-image-upload', file: File): void
  (e: 'cancel-image-upload'): void
  (e: 'dismiss-image-upload-error'): void
  (e: 'dismiss-failed-image-upload', file: File): void
}>()

const { t } = useI18n()
</script>

<template>
  <div
    v-if="isUploadingImage || hasImageUploadError"
    class="image-upload-status"
    :role="isUploadingImage ? 'status' : 'alert'"
    :aria-live="isUploadingImage ? 'polite' : 'assertive'"
  >
    <template v-if="isUploadingImage">
      <BaseSpinner size="sm" />
      <span>
        {{ t('board.writePost.upload.uploading') }}
        <span v-if="currentUploadingImageName" class="image-upload-status-name">{{ currentUploadingImageName }}</span>
        <span v-if="imageUploadQueueCount > 0" class="image-upload-status-count">+{{ imageUploadQueueCount }}</span>
      </span>
      <button type="button" class="image-upload-status-btn" @click="emit('cancel-image-upload')">
        {{ t('board.writePost.upload.cancel') }}
      </button>
    </template>
    <template v-else>
      <span>{{ t('common.messages.uploadFailed') }}<span v-if="failedImageCount > 1"> {{ failedImageCount }}</span></span>
      <div class="image-upload-failed-list">
        <div v-for="file in failedImageFiles" :key="`${file.name}-${file.size}-${file.lastModified}`" class="image-upload-failed-item">
          <span class="image-upload-status-name">{{ file.name }}</span>
          <button type="button" class="image-upload-status-btn" @click="emit('retry-failed-image-upload', file)">
            {{ t('board.writePost.upload.retry') }}
          </button>
          <button type="button" class="image-upload-status-btn image-upload-status-btn--icon" :aria-label="t('board.writePost.upload.dismiss')" @click="emit('dismiss-failed-image-upload', file)">
            <X class="h-4 w-4" aria-hidden="true" />
          </button>
        </div>
      </div>
      <button v-if="failedImageCount > 1" type="button" class="image-upload-status-btn" @click="emit('retry-image-upload')">
        {{ t('board.writePost.upload.retry') }}
      </button>
      <button type="button" class="image-upload-status-btn image-upload-status-btn--icon" :aria-label="t('board.writePost.upload.dismiss')" @click="emit('dismiss-image-upload-error')">
        <X class="h-4 w-4" aria-hidden="true" />
      </button>
    </template>
  </div>
</template>

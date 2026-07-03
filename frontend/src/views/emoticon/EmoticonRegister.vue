<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useHead } from '@unhead/vue'
import { ArrowLeft } from 'lucide-vue-next'
import { useToastStore } from '@/stores/toast'
import { useI18n } from 'vue-i18n'
import EmoticonFormActions from '@/components/emoticon/EmoticonFormActions.vue'
import EmoticonImageGridUploader from '@/components/emoticon/EmoticonImageGridUploader.vue'
import EmoticonTagSection from '@/components/emoticon/EmoticonTagSection.vue'
import EmoticonThumbnailField from '@/components/emoticon/EmoticonThumbnailField.vue'
import { useEmoticonImageSelection } from '@/features/emoticon/form/useEmoticonImageSelection'
import { useEmoticonImageFormState } from '@/features/emoticon/form/useEmoticonImageFormState'
import { useEmoticonRegisterSubmit } from '@/features/emoticon/form/useEmoticonRegisterSubmit'
import { useEmoticonTags } from '@/features/emoticon/form/useEmoticonTags'
import { useEmoticonUploadSession } from '@/features/emoticon/form/useEmoticonUploadSession'
import { SUPPORTED_EMOTICON_IMAGE_ACCEPT } from '@/utils/emoticonImage'

const { t } = useI18n()
const router = useRouter()
const toastStore = useToastStore()
const { selectThumbnailImage, selectEmoticonImages } = useEmoticonImageSelection(t, toastStore)

useHead({
  title: computed(() => t('emoticon.register.title'))
})

const emoticonName = ref('')
const isSubmitting = ref(false)
const uploadSession = useEmoticonUploadSession()
const { uploadProgress } = uploadSession
const { tagInput, tagItems, tags, addTag, removeTag } = useEmoticonTags({
  onMaxTags: () => {
    toastStore.addToast(t('emoticon.validation.maxTags'), 'error')
  }
})

const SUPPORTED_IMAGE_ACCEPT = SUPPORTED_EMOTICON_IMAGE_ACCEPT
const {
  thumbnailFile,
  thumbnailPreview,
  imagePreviews: emoticonPreviews,
  handleThumbnailSelect,
  removeThumbnail,
  handleEmoticonSelect,
  removeImagePreview: removeEmoticonImage,
} = useEmoticonImageFormState({
  selectThumbnailImage,
  selectEmoticonImages,
  getRemainingSlots: () => 100 - emoticonPreviews.value.length,
})

const isFormValid = computed(() => {
  return emoticonName.value.trim() !== '' && 
         thumbnailFile.value !== null &&
         emoticonPreviews.value.length > 0
})

const { handleSubmit } = useEmoticonRegisterSubmit({
  isFormValid,
  isSubmitting,
  thumbnailFile,
  emoticonPreviews,
  emoticonName,
  tags,
  uploadSession,
  fallbackErrorMessage: t('emoticon.register.failed'),
  onSuccess: () => {
    toastStore.addToast(t('emoticon.register.created'), 'success')
    router.push({ name: 'emoticon-list' })
  },
  onError: (message) => {
    toastStore.addToast(message, 'error')
  },
})

const goToList = () => {
  router.push({ name: 'emoticon-list' })
}
</script>


<template>
  <div class="max-w-4xl mx-auto py-8 px-4 sm:px-6 lg:px-8">
    <!-- 페이지 제목과 목록으로 버튼 -->
    <div class="mb-8 flex items-start justify-between">
      <div>
        <h1 class="text-2xl font-bold nv-title">{{ t('emoticon.register.title') }}</h1>
        <p class="mt-1 text-sm nv-text-subtle">{{ t('emoticon.register.description') }}</p>
      </div>
      <button
        @click="goToList"
        class="inline-flex items-center text-sm nv-text-muted hover:text-[var(--nv-accent)] transition-colors"
      >
        <ArrowLeft class="w-4 h-4 mr-1" />
        {{ t('emoticon.detail.backToList') }}
      </button>
    </div>

    <form @submit.prevent="handleSubmit" class="space-y-8">
      <!-- 이모티콘 이름과 썸네일 -->
      <div class="nv-surface rounded-lg shadow-sm border nv-border p-6">
        <div class="flex flex-col md:flex-row gap-6">
          <EmoticonThumbnailField
            input-id="emoticon-register-thumbnail-input"
            :accept="SUPPORTED_IMAGE_ACCEPT"
            :preview="thumbnailPreview"
            @change="handleThumbnailSelect"
            @remove="removeThumbnail"
          />

          <!-- 이모티콘 이름 -->
          <div class="order-1 md:order-2 flex-1">
            <label for="emoticon-register-name-input" class="block text-sm font-medium nv-text-muted mb-2">
              {{ t('emoticon.form.name') }} <span class="nv-form-error">*</span>
            </label>
            <input
              id="emoticon-register-name-input"
              v-model="emoticonName"
              type="text"
              name="emoticonName"
              autocomplete="off"
              maxlength="100"
              :placeholder="t('emoticon.form.namePlaceholder')"
              class="w-full px-4 py-2 border nv-border rounded-lg nv-surface nv-title placeholder:text-[var(--nv-text-subtle)] focus:ring-2 focus:ring-[var(--nv-focus)] focus:border-transparent"
            />
          </div>
        </div>
      </div>

      <EmoticonImageGridUploader
        input-id="emoticon-register-image-input"
        :accept="SUPPORTED_IMAGE_ACCEPT"
        :current-count="emoticonPreviews.length"
        :new-images="emoticonPreviews"
        @select="handleEmoticonSelect"
        @remove-new="removeEmoticonImage"
      />

      <!-- 태그 입력 -->
      <EmoticonTagSection
        v-model="tagInput"
        input-id="emoticon-register-tag-input"
        :tag-items="tagItems"
        :tag-count="tags.length"
        @add="addTag"
        @remove="removeTag"
      />

      <!-- 등록 버튼 -->
      <EmoticonFormActions
        :is-submitting="isSubmitting"
        :is-form-valid="isFormValid"
        :upload-progress="uploadProgress"
        :submit-text="t('emoticon.form.createSubmit')"
        :submitting-text="t('emoticon.form.creatingSubmit')"
      />
    </form>
  </div>
</template>

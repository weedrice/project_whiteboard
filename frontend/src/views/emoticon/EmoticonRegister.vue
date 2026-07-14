<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useHead } from '@unhead/vue'
import { ArrowLeft } from 'lucide-vue-next'
import { useToastStore } from '@/stores/toast'
import { useI18n } from 'vue-i18n'
import EmoticonFormActions from '@/components/emoticon/EmoticonFormActions.vue'
import EmoticonImageGridUploader from '@/components/emoticon/EmoticonImageGridUploader.vue'
import EmoticonTagSection from '@/components/emoticon/EmoticonTagSection.vue'
import EmoticonThumbnailField from '@/components/emoticon/EmoticonThumbnailField.vue'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import BaseCard from '@/components/common/ui/BaseCard.vue'
import PageHeader from '@/components/common/ui/PageHeader.vue'
import { useEmoticonImageSelection } from '@/features/emoticon/form/useEmoticonImageSelection'
import { useEmoticonImageFormState } from '@/features/emoticon/form/useEmoticonImageFormState'
import { useEmoticonRegisterSubmit } from '@/features/emoticon/form/useEmoticonRegisterSubmit'
import { useEmoticonTags } from '@/features/emoticon/form/useEmoticonTags'
import { useEmoticonUploadSession } from '@/features/emoticon/form/useEmoticonUploadSession'
import { useEmoticonImagePolicy } from '@/features/emoticon/form/useEmoticonImagePolicy'
import { SUPPORTED_EMOTICON_IMAGE_ACCEPT } from '@/utils/emoticonImage'

const { t } = useI18n()
const router = useRouter()
const toastStore = useToastStore()
const { maxImageCount, refresh: refreshImagePolicy } = useEmoticonImagePolicy()
const { selectThumbnailImage, selectEmoticonImages } = useEmoticonImageSelection(t, toastStore, {
  getMaxCount: () => maxImageCount.value,
})

onMounted(() => {
  void refreshImagePolicy()
})

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
  getRemainingSlots: () => Math.max(0, maxImageCount.value - emoticonPreviews.value.length),
})

const isFormValid = computed(() => {
  return emoticonName.value.trim() !== '' && 
         thumbnailFile.value !== null &&
         emoticonPreviews.value.length > 0 &&
         emoticonPreviews.value.length <= maxImageCount.value
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
  onLimitExceeded: () => {
    void refreshImagePolicy()
  },
})

const goToList = () => {
  router.push({ name: 'emoticon-list' })
}
</script>


<template>
  <div class="mx-auto max-w-4xl">
    <!-- 페이지 제목과 목록으로 버튼 -->
    <PageHeader
      :title="t('emoticon.register.title')"
      :description="t('emoticon.register.description')"
      class="mb-8"
    >
      <template #actions>
        <button
          type="button"
          @click="goToList"
          class="nv-focus-ring inline-flex min-h-11 items-center rounded-md px-2 text-sm nv-text-muted hover:text-[var(--nv-accent)] transition-colors"
        >
          <ArrowLeft class="w-4 h-4 mr-1" />
          {{ t('emoticon.detail.backToList') }}
        </button>
      </template>
    </PageHeader>

    <form @submit.prevent="handleSubmit" class="space-y-8">
      <!-- 이모티콘 이름과 썸네일 -->
      <BaseCard padding="lg" bordered>
        <div class="flex flex-col md:flex-row gap-6">
          <EmoticonThumbnailField
            input-id="emoticon-register-thumbnail-input"
            :accept="SUPPORTED_IMAGE_ACCEPT"
            :preview="thumbnailPreview"
            @change="handleThumbnailSelect"
            @remove="removeThumbnail"
          />

          <!-- 이모티콘 이름 -->
          <div class="order-1 flex-1 md:order-2">
            <BaseInput
              id="emoticon-register-name-input"
              v-model="emoticonName"
              type="text"
              name="emoticonName"
              required
              autocomplete="off"
              maxlength="100"
              :label="t('emoticon.form.name')"
              :placeholder="t('emoticon.form.namePlaceholder')"
              input-class="rounded-lg px-4"
            />
          </div>
        </div>
      </BaseCard>

      <EmoticonImageGridUploader
        input-id="emoticon-register-image-input"
        :accept="SUPPORTED_IMAGE_ACCEPT"
        :current-count="emoticonPreviews.length"
        :max-count="maxImageCount"
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

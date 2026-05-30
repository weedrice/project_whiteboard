<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useHead } from '@unhead/vue'
import { ArrowLeft, Upload, X, Plus } from 'lucide-vue-next'
import { useToastStore } from '@/stores/toast'
import { useI18n } from 'vue-i18n'
import EmoticonFormActions from '@/components/emoticon/EmoticonFormActions.vue'
import EmoticonImageTile from '@/components/emoticon/EmoticonImageTile.vue'
import EmoticonTagSection from '@/components/emoticon/EmoticonTagSection.vue'
import { useEmoticonImageSelection } from '@/composables/useEmoticonImageSelection'
import { useEmoticonImageFormState } from '@/composables/useEmoticonImageFormState'
import { useEmoticonRegisterSubmit } from '@/composables/useEmoticonRegisterSubmit'
import { useEmoticonTags } from '@/composables/useEmoticonTags'
import { useEmoticonUploadSession } from '@/composables/useEmoticonUploadSession'
import { SUPPORTED_EMOTICON_IMAGE_ACCEPT } from '@/utils/emoticonImage'

const { t } = useI18n()
const router = useRouter()
const toastStore = useToastStore()
const { selectThumbnailImage, selectEmoticonImages } = useEmoticonImageSelection(t, toastStore)

useHead({
  title: '노비콘 등록'
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
  thumbnailInput,
  emoticonInput,
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
        <h1 class="text-2xl font-bold nv-title">노비콘 등록</h1>
        <p class="mt-1 text-sm nv-text-subtle">나만의 노비콘을 등록해보세요!</p>
      </div>
      <button
        @click="goToList"
        class="inline-flex items-center text-sm nv-text-muted hover:text-[var(--nv-accent)] transition-colors"
      >
        <ArrowLeft class="w-4 h-4 mr-1" />
        목록으로
      </button>
    </div>

    <form @submit.prevent="handleSubmit" class="space-y-8">
      <!-- 이모티콘 이름과 썸네일 -->
      <div class="nv-surface rounded-lg shadow-sm border nv-border p-6">
        <div class="flex flex-col md:flex-row gap-6">
          <!-- 썸네일 업로드 -->
          <div class="order-2 md:order-1 shrink-0">
            <label for="emoticon-register-thumbnail-input" class="block text-sm font-medium nv-text-muted mb-2">
              썸네일 이미지 <span class="nv-form-error">*</span>
            </label>
            <p class="text-xs nv-text-subtle mb-4">대표 이미지로 노비콘 목록에 표시됩니다. 500x500px 이하의 이미지만 업로드 가능합니다.</p>
            <input
              id="emoticon-register-thumbnail-input"
              ref="thumbnailInput"
              type="file"
              name="thumbnailImage"
              :accept="SUPPORTED_IMAGE_ACCEPT"
              @change="handleThumbnailSelect"
              class="hidden"
            />

            <div v-if="thumbnailPreview" class="relative inline-block">
              <img
                :src="thumbnailPreview"
                alt="썸네일 미리보기"
                class="w-32 h-32 object-contain nv-surface-muted rounded-lg"
              />
              <button
                type="button"
                @click="removeThumbnail"
                :aria-label="$t('common.delete')"
                :title="$t('common.delete')"
                class="absolute -top-2 -right-2 w-6 h-6 bg-[var(--nv-danger)] text-white rounded-full flex items-center justify-center hover:brightness-95"
              >
                <X class="w-4 h-4" />
              </button>
            </div>
            <div v-else>
              <button
                type="button"
                @click="thumbnailInput?.click()"
                class="w-32 h-32 border-2 border-dashed nv-border rounded-lg flex flex-col items-center justify-center nv-text-subtle hover:border-[var(--nv-focus)] hover:text-[var(--nv-accent)] transition-colors"
              >
                <Upload class="w-8 h-8 mb-2" />
                <span class="text-xs">이미지 선택</span>
              </button>
            </div>
          </div>

          <!-- 이모티콘 이름 -->
          <div class="order-1 md:order-2 flex-1">
            <label for="emoticon-register-name-input" class="block text-sm font-medium nv-text-muted mb-2">
              이모티콘 이름 <span class="nv-form-error">*</span>
            </label>
            <input
              id="emoticon-register-name-input"
              v-model="emoticonName"
              type="text"
              name="emoticonName"
              autocomplete="off"
              maxlength="100"
              placeholder="이모티콘 이름을 입력하세요"
              class="w-full px-4 py-2 border nv-border rounded-lg nv-surface nv-title placeholder:text-[var(--nv-text-subtle)] focus:ring-2 focus:ring-[var(--nv-focus)] focus:border-transparent"
            />
          </div>
        </div>
      </div>

      <!-- 이모티콘 이미지 업로드 -->
      <div class="nv-surface rounded-lg shadow-sm border nv-border p-6">
        <label for="emoticon-register-image-input" class="block text-sm font-medium nv-text-muted mb-2">
          이모티콘 이미지 <span class="nv-form-error">*</span>
          <span class="text-xs font-normal nv-text-subtle ml-2">({{ emoticonPreviews.length }}/100개)</span>
        </label>
        <p class="text-xs nv-text-subtle mb-4">
          최대 100개까지 업로드 가능합니다. 500x500px 이하의 이미지만 업로드 가능하며, 100px 초과 시 자동으로 리사이징됩니다.
        </p>

        <!-- 이미지 그리드 -->
        <div class="grid grid-cols-5 sm:grid-cols-8 md:grid-cols-10 gap-2 mb-4">
          <EmoticonImageTile
            v-for="(item, index) in emoticonPreviews"
            :key="item.clientId"
            :src="item.preview"
            :alt="`이모티콘 ${index + 1}`"
            :action-label="$t('common.delete')"
            :action-title="$t('common.delete')"
            action="delete"
            @action="removeEmoticonImage(item.clientId)"
          />

          <!-- 추가 버튼 -->
          <input
            id="emoticon-register-image-input"
            ref="emoticonInput"
            type="file"
            name="emoticonImages"
            :accept="SUPPORTED_IMAGE_ACCEPT"
            multiple
            :disabled="emoticonPreviews.length >= 100"
            @change="handleEmoticonSelect"
            class="hidden"
          />
          <div v-if="emoticonPreviews.length < 100">
            <button
              type="button"
              @click="emoticonInput?.click()"
              :aria-label="$t('common.add')"
              :title="$t('common.add')"
              class="w-full aspect-square border-2 border-dashed nv-border rounded flex flex-col items-center justify-center nv-text-subtle hover:border-[var(--nv-focus)] hover:text-[var(--nv-accent)] transition-colors"
              style="width: 100px; height: 100px;"
            >
              <Plus class="w-6 h-6" />
            </button>
          </div>
        </div>
      </div>

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
        submit-text="등록하기"
        submitting-text="등록 중..."
      />
    </form>
  </div>
</template>

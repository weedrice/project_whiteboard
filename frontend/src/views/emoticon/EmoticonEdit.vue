<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useQueryClient } from '@tanstack/vue-query'
import { useHead } from '@unhead/vue'
import { ArrowLeft, Upload, X, Plus, EyeOff, Eye } from 'lucide-vue-next'
import { useToastStore } from '@/stores/toast'
import { useI18n } from 'vue-i18n'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import EmoticonFormActions from '@/components/emoticon/EmoticonFormActions.vue'
import EmoticonImageTile from '@/components/emoticon/EmoticonImageTile.vue'
import EmoticonTagSection from '@/components/emoticon/EmoticonTagSection.vue'
import { useConfirm } from '@/composables/useConfirm'
import { useEmoticonEditForm } from '@/composables/useEmoticonEditForm'
import { useEmoticonEditResource } from '@/composables/useEmoticonEditResource'
import { useEmoticonEditSubmit } from '@/composables/useEmoticonEditSubmit'
import { useEmoticonImageSelection } from '@/composables/useEmoticonImageSelection'
import { SUPPORTED_EMOTICON_IMAGE_ACCEPT } from '@/utils/emoticonImage'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const toastStore = useToastStore()
const queryClient = useQueryClient()
const { selectThumbnailImage, selectEmoticonImages } = useEmoticonImageSelection(t, toastStore)
const { confirm } = useConfirm()

const emoticonId = computed(() => Number(route.params.emoticonId))
const { emoticon, editFormState, isLoading } = useEmoticonEditResource({ emoticonId })

useHead({
  title: computed(() => emoticon.value?.name ? `${emoticon.value.name} 수정 - 노비콘` : '노비콘 수정')
})

const SUPPORTED_IMAGE_ACCEPT = SUPPORTED_EMOTICON_IMAGE_ACCEPT
const {
  emoticonName,
  existingImages,
  imagesToDelete,
  isSubmitting,
  uploadSession,
  uploadProgress,
  tagInput,
  tagItems,
  tags,
  addTag,
  removeTag,
  thumbnailFile,
  thumbnailPreview,
  newEmoticonPreviews,
  thumbnailInput,
  emoticonInput,
  handleThumbnailSelect,
  handleEmoticonSelect,
  removeNewEmoticonImage,
  changeThumbnail,
  isToggling,
  handleToggleVisibility,
  markImageForDeletion,
  unmarkImageForDeletion,
  totalImageCount,
  isFormValid,
} = useEmoticonEditForm({
  emoticonId,
  emoticon,
  editFormState,
  selectThumbnailImage,
  selectEmoticonImages,
  confirm,
  t,
  onMaxTags: () => {
    toastStore.addToast(t('emoticon.validation.maxTags'), 'error')
  }
})

const { handleSubmit } = useEmoticonEditSubmit({
  emoticonId,
  isFormValid,
  isSubmitting,
  thumbnailFile,
  imagesToDelete,
  newEmoticonPreviews,
  emoticonName,
  tags,
  uploadSession,
  queryClient,
  fallbackErrorMessage: t('emoticon.edit.failed'),
  onSuccess: () => {
    toastStore.addToast(t('emoticon.edit.updated'), 'success')
    router.push({ name: 'emoticon-detail', params: { emoticonId: emoticonId.value } })
  },
  onError: (message) => {
    toastStore.addToast(message, 'error')
  },
})

const goToDetail = () => {
  router.push({ name: 'emoticon-detail', params: { emoticonId: emoticonId.value } })
}
</script>


<template>
  <div class="max-w-4xl mx-auto py-8 px-4 sm:px-6 lg:px-8">
    <!-- 페이지 제목과 뒤로가기 버튼 -->
    <div class="mb-8 flex items-start justify-between">
      <div>
        <h1 class="text-2xl font-bold nv-title">노비콘 수정</h1>
        <p class="mt-1 text-sm nv-text-subtle">노비콘 정보를 수정합니다.</p>
      </div>
      <button @click="goToDetail"
        class="inline-flex items-center text-sm nv-text-muted hover:text-[var(--nv-accent)] transition-colors">
        <ArrowLeft class="w-4 h-4 mr-1" />
        뒤로
      </button>
    </div>

    <!-- 로딩 -->
    <div v-if="isLoading" class="animate-pulse space-y-8">
      <div class="nv-surface rounded-lg border nv-border p-6">
        <div class="flex gap-6">
          <div class="w-32 h-32 nv-surface-muted rounded-lg"></div>
          <div class="flex-1">
            <div class="h-10 nv-surface-muted rounded w-full"></div>
          </div>
        </div>
      </div>
      <div class="nv-surface rounded-lg border nv-border p-6">
        <div class="grid grid-cols-5 gap-2">
          <div v-for="i in 5" :key="i" class="aspect-square nv-surface-muted rounded"></div>
        </div>
      </div>
    </div>

    <!-- 폼 -->
    <form v-else-if="emoticon" @submit.prevent="handleSubmit" class="space-y-8">
      <!-- 숨김/표시 전환 (등록자만) -->
      <div class="nv-surface rounded-lg shadow-sm border nv-border p-4">
        <div class="flex items-center justify-between">
          <div>
            <span v-if="!emoticon.isActive"
              class="inline-flex items-center px-2.5 py-0.5 rounded text-xs font-medium nv-surface-muted nv-text-muted">
              {{ $t('emoticon.visibility.hidden') }}
            </span>
            <span v-else class="text-sm nv-text-subtle">현재 판매 중입니다</span>
          </div>
          <button type="button" @click="handleToggleVisibility" :disabled="isToggling"
            :class="emoticon.isActive
              ? 'inline-flex items-center px-3 py-1.5 text-sm nv-status-warning nv-hover-surface rounded-lg transition-colors'
              : 'inline-flex items-center px-3 py-1.5 text-sm nv-status-success nv-hover-surface rounded-lg transition-colors'">
            <EyeOff v-if="emoticon.isActive" class="w-4 h-4 mr-1" />
            <Eye v-else class="w-4 h-4 mr-1" />
            {{ emoticon.isActive ? $t('emoticon.visibility.hide') : $t('emoticon.visibility.show') }}
          </button>
        </div>
      </div>

      <!-- 이모티콘 이름과 썸네일 -->
      <div class="nv-surface rounded-lg shadow-sm border nv-border p-6">
        <div class="flex flex-col md:flex-row gap-6">
          <!-- 썸네일 -->
          <div class="order-2 md:order-1 shrink-0">
            <label for="emoticon-thumbnail-input" class="block text-sm font-medium nv-text-muted mb-2">
              썸네일 이미지 <span class="nv-form-error">*</span>
            </label>
            <p class="text-xs nv-text-subtle mb-4">대표 이미지로 노비콘 목록에 표시됩니다. 500x500px 이하의 이미지만 업로드
              가능합니다.</p>

            <div class="relative inline-block">
              <img v-if="thumbnailPreview" :src="thumbnailPreview" alt="썸네일 미리보기"
                class="w-32 h-32 object-contain nv-surface-muted rounded-lg cursor-pointer hover:opacity-80 transition-opacity"
                @click="changeThumbnail" title="클릭하여 이미지 변경" />
              <div v-else
                class="w-32 h-32 nv-surface-muted rounded-lg flex items-center justify-center nv-text-subtle">
                No Image
              </div>
              <input id="emoticon-thumbnail-input" ref="thumbnailInput" type="file" name="thumbnailImage" :accept="SUPPORTED_IMAGE_ACCEPT" @change="handleThumbnailSelect" class="hidden" />
              <button type="button" @click="changeThumbnail"
                :aria-label="$t('common.edit')"
                class="absolute -bottom-2 -right-2 w-8 h-8 bg-[var(--nv-accent)] text-white rounded-full flex items-center justify-center hover:brightness-95 shadow-md"
                :title="$t('common.edit')">
                <Upload class="w-4 h-4" />
              </button>
            </div>
          </div>

          <!-- 이모티콘 이름 -->
          <div class="order-1 md:order-2 flex-1">
            <label for="emoticon-name-input" class="block text-sm font-medium nv-text-muted mb-2">
              이모티콘 이름 <span class="nv-form-error">*</span>
            </label>
            <input id="emoticon-name-input" v-model="emoticonName" type="text" name="emoticonName" autocomplete="off" maxlength="100" placeholder="이모티콘 이름을 입력하세요"
              class="w-full px-4 py-2 border nv-border rounded-lg nv-surface nv-title placeholder:text-[var(--nv-text-subtle)] focus:ring-2 focus:ring-[var(--nv-focus)] focus:border-transparent" />
          </div>
        </div>
      </div>

      <!-- 이모티콘 이미지 -->
      <div class="nv-surface rounded-lg shadow-sm border nv-border p-6">
        <label for="emoticon-image-input" class="block text-sm font-medium nv-text-muted mb-2">
          이모티콘 이미지 <span class="nv-form-error">*</span>
          <span class="text-xs font-normal nv-text-subtle ml-2">({{ totalImageCount }}/100개)</span>
        </label>
        <p class="text-xs nv-text-subtle mb-4">
          최대 100개까지 업로드 가능합니다. 500x500px 이하의 이미지만 업로드 가능하며, 100px 초과 시 자동으로 리사이징됩니다.
        </p>

        <!-- 이미지 그리드 -->
        <div class="grid grid-cols-5 sm:grid-cols-8 md:grid-cols-10 gap-2 mb-4">
          <!-- 기존 이미지 -->
          <EmoticonImageTile
            v-for="image in existingImages"
            :key="'existing-' + image.imageId"
            :src="image.imageUrl"
            :alt="`이모티콘 ${image.sortOrder + 1}`"
            :muted="imagesToDelete.includes(image.imageId)"
            :action="imagesToDelete.includes(image.imageId) ? 'cancel' : 'delete'"
            :action-label="imagesToDelete.includes(image.imageId) ? $t('common.cancel') : $t('common.delete')"
            :action-title="imagesToDelete.includes(image.imageId) ? $t('common.cancel') : $t('common.delete')"
            @action="imagesToDelete.includes(image.imageId)
              ? unmarkImageForDeletion(image.imageId)
              : markImageForDeletion(image.imageId)"
          />

          <!-- 새로 추가할 이미지 -->
          <EmoticonImageTile
            v-for="(item, index) in newEmoticonPreviews"
            :key="item.clientId"
            :src="item.preview"
            :alt="`새 이모티콘 ${index + 1}`"
            :action-label="$t('common.delete')"
            :action-title="$t('common.delete')"
            action="delete"
            variant="new"
            @action="removeNewEmoticonImage(item.clientId)"
          />

          <!-- 추가 버튼 -->
          <input id="emoticon-image-input" ref="emoticonInput" type="file" name="emoticonImages" :accept="SUPPORTED_IMAGE_ACCEPT" multiple :disabled="totalImageCount >= 100" @change="handleEmoticonSelect"
            class="hidden" />
          <div v-if="totalImageCount < 100">
            <button type="button" @click="emoticonInput?.click()"
              :aria-label="$t('common.add')"
              :title="$t('common.add')"
              class="w-full aspect-square border-2 border-dashed nv-border rounded flex flex-col items-center justify-center nv-text-subtle hover:border-[var(--nv-focus)] hover:text-[var(--nv-accent)] transition-colors"
              style="width: 100px; height: 100px;">
              <Plus class="w-6 h-6" />
            </button>
          </div>
        </div>

        <!-- 변경 안내 -->
        <div v-if="imagesToDelete.length > 0 || newEmoticonPreviews.length > 0"
          class="text-xs nv-text-subtle mt-2">
          <span v-if="imagesToDelete.length > 0" class="nv-form-error">{{ imagesToDelete.length }}개 삭제 예정</span>
          <span v-if="imagesToDelete.length > 0 && newEmoticonPreviews.length > 0"> · </span>
          <span v-if="newEmoticonPreviews.length > 0" class="text-[var(--nv-success-text)]">{{ newEmoticonPreviews.length }}개 추가
            예정</span>
        </div>
      </div>

      <!-- 태그 입력 -->
      <EmoticonTagSection
        v-model="tagInput"
        input-id="emoticon-tag-input"
        :tag-items="tagItems"
        :tag-count="tags.length"
        @add="addTag"
        @remove="removeTag"
      />

      <!-- 수정 버튼 -->
      <EmoticonFormActions
        :is-submitting="isSubmitting"
        :is-form-valid="isFormValid"
        :upload-progress="uploadProgress"
        submit-text="수정하기"
        submitting-text="수정 중..."
      >
        <template #before-submit>
          <BaseButton type="button" @click="goToDetail" variant="secondary" size="lg">
            취소
          </BaseButton>
        </template>
      </EmoticonFormActions>
    </form>
  </div>
</template>

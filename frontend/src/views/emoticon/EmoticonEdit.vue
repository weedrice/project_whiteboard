<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { onBeforeRouteUpdate, useRoute, useRouter } from 'vue-router'
import { useQueryClient } from '@tanstack/vue-query'
import { useHead } from '@unhead/vue'
import { ArrowLeft, EyeOff, Eye } from 'lucide-vue-next'
import { useToastStore } from '@/stores/toast'
import { useI18n } from 'vue-i18n'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseCard from '@/components/common/ui/BaseCard.vue'
import BaseBadge from '@/components/common/ui/BaseBadge.vue'
import PageHeader from '@/components/common/ui/PageHeader.vue'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import EmoticonFormActions from '@/components/emoticon/EmoticonFormActions.vue'
import EmoticonImageGridUploader from '@/components/emoticon/EmoticonImageGridUploader.vue'
import EmoticonTagSection from '@/components/emoticon/EmoticonTagSection.vue'
import EmoticonThumbnailField from '@/components/emoticon/EmoticonThumbnailField.vue'
import { useConfirm } from '@/composables/useConfirm'
import { useEmoticonEditForm } from '@/features/emoticon/form/useEmoticonEditForm'
import { useEmoticonEditResource } from '@/features/emoticon/form/useEmoticonEditResource'
import { useEmoticonEditSubmit } from '@/features/emoticon/form/useEmoticonEditSubmit'
import { useEmoticonImageSelection } from '@/features/emoticon/form/useEmoticonImageSelection'
import { useEmoticonImagePolicy } from '@/features/emoticon/form/useEmoticonImagePolicy'
import { SUPPORTED_EMOTICON_IMAGE_ACCEPT } from '@/utils/emoticonImage'
import { useUnsavedChangesGuard } from '@/composables/useUnsavedChangesGuard'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const toastStore = useToastStore()
const queryClient = useQueryClient()
const { maxImageCount, refresh: refreshImagePolicy } = useEmoticonImagePolicy()
const { selectThumbnailImage, selectEmoticonImages } = useEmoticonImageSelection(t, toastStore, {
  getMaxCount: () => maxImageCount.value,
})
const { confirm } = useConfirm()

onMounted(() => {
  void refreshImagePolicy()
})

const emoticonId = computed(() => Number(route.params.emoticonId))
const { emoticon, editFormState, isLoading } = useEmoticonEditResource({ emoticonId })

useHead({
  title: computed(() => emoticon.value?.name ? `${emoticon.value.name} ${t('common.edit')} - ${t('emoticon.title')}` : t('emoticon.form.editTitle'))
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
  handleThumbnailSelect,
  handleEmoticonSelect,
  removeNewEmoticonImage,
  isToggling,
  handleToggleVisibility,
  markImageForDeletion,
  unmarkImageForDeletion,
  totalImageCount,
  isFormValid,
  hasUnsavedChanges,
  canAddImages,
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
  },
  maxImageCount,
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
  onSuccess: (updatedEmoticonId) => {
    toastStore.addToast(t('emoticon.edit.updated'), 'success')
    allowNextNavigation()
    router.push({ name: 'emoticon-detail', params: { emoticonId: updatedEmoticonId } })
  },
  onError: (message) => {
    toastStore.addToast(message, 'error')
  },
  onLimitExceeded: () => {
    void refreshImagePolicy()
  },
})

const goToDetail = () => {
  if (isSubmitting.value) return
  router.push({ name: 'emoticon-detail', params: { emoticonId: emoticonId.value } })
}

const { allowNextNavigation, confirmNavigation } = useUnsavedChangesGuard(
  hasUnsavedChanges, isSubmitting, () => t('emoticon.form.leaveConfirm'), confirm,
)
onBeforeRouteUpdate(confirmNavigation)
</script>


<template>
  <div class="mx-auto max-w-4xl">
    <!-- 페이지 제목과 뒤로가기 버튼 -->
    <PageHeader
      :title="t('emoticon.form.editTitle')"
      :description="t('emoticon.form.editDescription')"
      class="mb-8"
    >
      <template #actions>
        <BaseButton variant="ghost" size="sm" :disabled="isSubmitting" @click="goToDetail">
          <ArrowLeft class="w-4 h-4 mr-1" />
          {{ t('emoticon.form.back') }}
        </BaseButton>
      </template>
    </PageHeader>

    <!-- 로딩 -->
    <div v-if="isLoading" class="animate-pulse space-y-8" role="status" aria-live="polite" aria-busy="true"
      :aria-label="t('common.loading')">
      <BaseCard bordered elevation="none" padding="lg">
        <div class="flex gap-6">
          <div class="w-32 h-32 nv-surface-muted rounded-lg"></div>
          <div class="flex-1">
            <div class="h-10 nv-surface-muted rounded w-full"></div>
          </div>
        </div>
      </BaseCard>
      <BaseCard bordered elevation="none" padding="lg">
        <div class="grid grid-cols-5 gap-2">
          <div v-for="i in 5" :key="i" class="aspect-square nv-surface-muted rounded"></div>
        </div>
      </BaseCard>
    </div>

    <!-- 폼 -->
    <form v-else-if="emoticon" @submit.prevent="handleSubmit">
      <fieldset
        :disabled="isSubmitting"
        :inert="isSubmitting ? true : undefined"
        :aria-busy="isSubmitting ? 'true' : undefined"
        class="m-0 min-w-0 space-y-8 border-0 p-0"
      >
      <!-- 숨김/표시 전환 (등록자만) -->
      <BaseCard bordered padding="sm">
        <div class="flex items-center justify-between">
          <div>
            <BaseBadge v-if="!emoticon.isActive" variant="gray" size="sm" :rounded="false">
              {{ $t('emoticon.visibility.hidden') }}
            </BaseBadge>
            <span v-else class="text-sm nv-text-subtle">{{ t('emoticon.form.onSale') }}</span>
          </div>
          <button type="button" @click="handleToggleVisibility" :disabled="isToggling || isSubmitting"
            :class="emoticon.isActive
              ? 'nv-focus-ring inline-flex min-h-11 items-center px-3 py-1.5 text-sm nv-status-warning nv-hover-surface rounded-lg transition-colors'
              : 'nv-focus-ring inline-flex min-h-11 items-center px-3 py-1.5 text-sm nv-status-success nv-hover-surface rounded-lg transition-colors'">
            <EyeOff v-if="emoticon.isActive" class="w-4 h-4 mr-1" />
            <Eye v-else class="w-4 h-4 mr-1" />
            {{ emoticon.isActive ? $t('emoticon.visibility.hide') : $t('emoticon.visibility.show') }}
          </button>
        </div>
      </BaseCard>

      <!-- 이모티콘 이름과 썸네일 -->
      <BaseCard bordered padding="lg">
        <div class="flex flex-col md:flex-row gap-6">
          <EmoticonThumbnailField
            input-id="emoticon-thumbnail-input"
            mode="edit"
            :accept="SUPPORTED_IMAGE_ACCEPT"
            :preview="thumbnailPreview"
            @change="handleThumbnailSelect"
          />

          <!-- 이모티콘 이름 -->
          <div class="order-1 flex-1 md:order-2">
            <BaseInput
              id="emoticon-name-input"
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
        input-id="emoticon-image-input"
        :accept="SUPPORTED_IMAGE_ACCEPT"
        :current-count="totalImageCount"
        :max-count="maxImageCount"
        :allow-add="canAddImages"
        :new-images="newEmoticonPreviews"
        :existing-images="existingImages"
        :images-to-delete="imagesToDelete"
        @select="handleEmoticonSelect"
        @remove-new="removeNewEmoticonImage"
        @mark-delete="markImageForDeletion"
        @unmark-delete="unmarkImageForDeletion"
      >
        <template #meta>
          <div v-if="imagesToDelete.length > 0 || newEmoticonPreviews.length > 0"
            class="text-xs nv-text-subtle mt-2">
          <span v-if="imagesToDelete.length > 0" class="nv-form-error">{{ t('emoticon.form.deletePending', { count: imagesToDelete.length }) }}</span>
          <span v-if="imagesToDelete.length > 0 && newEmoticonPreviews.length > 0"> · </span>
          <span v-if="newEmoticonPreviews.length > 0" class="text-[var(--nv-success-text)]">{{ t('emoticon.form.addPending', { count: newEmoticonPreviews.length }) }}</span>
          </div>
        </template>
      </EmoticonImageGridUploader>

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
        :submit-text="t('emoticon.form.updateSubmit')"
        :submitting-text="t('emoticon.form.updatingSubmit')"
      >
        <template #before-submit>
          <BaseButton type="button" :disabled="isSubmitting" @click="goToDetail" variant="secondary" size="lg">
            {{ t('common.cancel') }}
          </BaseButton>
        </template>
      </EmoticonFormActions>
      </fieldset>
    </form>
  </div>
</template>

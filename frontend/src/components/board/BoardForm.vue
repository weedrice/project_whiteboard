<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseTextarea from '@/components/common/ui/BaseTextarea.vue'
import BaseCheckbox from '@/components/common/ui/BaseCheckbox.vue'
import BaseSelect from '@/components/common/ui/BaseSelect.vue'
import { useBoardCreationPolicy } from '@/features/board/form/useBoardCreationPolicy'
import { useBoardFormState, type BoardFormData } from '@/features/board/form/useBoardFormState'
import {
  useBoardFormSubmit,
  type BoardFormSubmitAction,
} from '@/features/board/form/useBoardFormSubmit'
import { IMAGE_UPLOAD_ACCEPT } from '@/utils/imageUploadPolicy'
import { useAuthStore } from '@/stores/auth'
import { BOARD_WRITE_LIMITS } from '@/utils/board'
import { usePwaReloadBlocker } from '@/pwaReloadGuard'
import { useI18n } from 'vue-i18n'

type BoardData = BoardFormData
type BoardVisibility = 'PUBLIC' | 'UNLISTED' | 'PRIVATE'

const props = withDefaults(defineProps<{
  initialData?: BoardData
  isEdit?: boolean
  isSubmitting?: boolean
  error?: string
  submitAction?: BoardFormSubmitAction
}>(), {
  initialData: () => ({
    boardName: '',
    boardUrl: '',
    description: '',
    iconUrl: '',
    sortOrder: 0,
    allowNsfw: false,
    isPublic: true,
    isListed: true,
    agentUseYn: false,
    guidePrompt: ''
  }),
  isEdit: false,
  isSubmitting: false,
  error: ''
})

const emit = defineEmits<{
  (e: 'submit', data: BoardData): void
  (e: 'cancel'): void
}>()

function formSnapshot(data: BoardData) {
  return JSON.stringify({
    ...data,
    isListed: data.isListed ?? data.isPublic,
    agentUseYn: data.isPublic ? data.agentUseYn : false,
  })
}

const savedSnapshot = ref(formSnapshot(props.initialData))
const savedSelectedFile = ref<File | null>(null)

const isEditMode = computed(() => props.isEdit)
const { t } = useI18n()
const authStore = useAuthStore()
const {
  userPoints,
  boardCreateCost,
  isBoardCreateCostLoading,
  boardCreateCostError,
  canCreate,
  loadBoardCreateCost,
} = useBoardCreationPolicy({ isEdit: isEditMode })

const {
  form,
  selectedFile,
  previewImage,
  handleFileChange,
} = useBoardFormState({
  initialData: () => props.initialData,
  isEdit: () => props.isEdit,
})

const {
  isSubmitting: localIsSubmitting,
  handleSubmit,
  cancelSubmission,
} = useBoardFormSubmit({
  form,
  selectedFile,
  isEdit: () => props.isEdit,
  canCreate,
  boardCreateCost,
  submitAction: props.submitAction,
  emitSubmit: (data) => emit('submit', data),
  getSessionGeneration: () => authStore.sessionGeneration,
  markSaved: markCurrentSnapshotSaved,
})

const isSubmitting = computed(() => props.isSubmitting || localIsSubmitting.value)
const visibility = computed<BoardVisibility>({
  get: () => {
    if (!form.value.isPublic) return 'PRIVATE'
    return form.value.isListed ? 'PUBLIC' : 'UNLISTED'
  },
  set: (value: BoardVisibility) => {
    form.value.isPublic = value !== 'PRIVATE'
    form.value.isListed = value === 'PUBLIC'
  },
})
const visibilityOptions = computed(() => [
  { value: 'PUBLIC', label: t('board.form.visibilityPublic') },
  { value: 'UNLISTED', label: t('board.form.visibilityUnlisted') },
  { value: 'PRIVATE', label: t('board.form.visibilityPrivate') },
])
const visibilityDescription = computed(() => {
  if (visibility.value === 'PUBLIC') return t('board.form.visibilityPublicDesc')
  if (visibility.value === 'UNLISTED') return t('board.form.visibilityUnlistedDesc')
  return t('board.form.visibilityPrivateDesc')
})
let allowSuccessfulRouteLeave = false
const hasUnsavedChangesState = computed(() => (
  selectedFile.value !== savedSelectedFile.value || formSnapshot(form.value) !== savedSnapshot.value
))

watch(() => props.initialData, (initialData) => {
  savedSnapshot.value = formSnapshot(initialData)
  savedSelectedFile.value = null
}, { deep: true })

function hasUnsavedChanges() {
  return hasUnsavedChangesState.value
}

function markCurrentSnapshotSaved() {
  savedSnapshot.value = formSnapshot(form.value)
  savedSelectedFile.value = selectedFile.value
  allowSuccessfulRouteLeave = true
}

function consumeSuccessfulSubmissionNavigation() {
  if (!allowSuccessfulRouteLeave) return false
  allowSuccessfulRouteLeave = false
  return true
}

function handleCancel() {
  if (isSubmitting.value) return
  cancelSubmission()
  emit('cancel')
}

function handleBeforeUnload(event: BeforeUnloadEvent) {
  if (!hasUnsavedChanges() && !isSubmitting.value) return
  event.preventDefault()
  event.returnValue = ''
}

usePwaReloadBlocker(hasUnsavedChangesState)
onMounted(() => window.addEventListener('beforeunload', handleBeforeUnload))
onBeforeUnmount(() => window.removeEventListener('beforeunload', handleBeforeUnload))

defineExpose({
  hasUnsavedChanges,
  isSubmissionInProgress: () => isSubmitting.value,
  consumeSuccessfulSubmissionNavigation,
  markCurrentSnapshotSaved,
})
</script>

<template>
  <form @submit.prevent="handleSubmit"
    class="space-y-6 nv-surface nv-elevated-surface shadow px-4 py-5 sm:rounded-lg sm:p-6 transition-colors duration-200">
    <div v-if="error" class="rounded-md nv-status-danger p-4" role="alert" aria-live="assertive">
      <div class="flex">
        <div class="ml-3">
          <p class="text-sm font-medium">{{ error }}</p>
        </div>
      </div>
    </div>

    <fieldset
      class="grid grid-cols-1 gap-y-8 gap-x-4 sm:grid-cols-6"
      :disabled="isSubmitting"
      :inert="isSubmitting"
      :aria-busy="isSubmitting"
    >

      <!-- Board Name + Icon Upload Row -->
      <div class="sm:col-span-6 flex items-end gap-4">
        <!-- Image Preview & Input -->
        <div class="shrink-0 relative group">
          <label for="icon-upload" class="cursor-pointer">
            <div
              class="h-16 w-16 rounded-md border nv-border nv-surface-muted flex items-center justify-center overflow-hidden nv-hover-surface transition-colors duration-200">
              <img v-if="previewImage" :src="previewImage" :alt="$t('board.form.iconImage')" class="h-full w-full object-cover" />
              <svg v-else class="h-8 w-8 nv-text-subtle" fill="none" stroke="currentColor"
                viewBox="0 0 24 24" aria-hidden="true">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                  d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
              </svg>
            </div>
            <input id="icon-upload" type="file" @change="handleFileChange" :accept="IMAGE_UPLOAD_ACCEPT"
              :disabled="isSubmitting" class="hidden" />
            <span class="text-xs nv-text-subtle text-center block mt-1">{{ $t('board.form.iconImage')
            }}</span>
          </label>
        </div>

        <div class="flex-1">
          <BaseInput :label="$t('board.form.name')" v-model="form.boardName" type="text" required
            :maxlength="BOARD_WRITE_LIMITS.boardName"
            :placeholder="$t('board.form.placeholder.name')" labelClass="text-base" />
        </div>
      </div>

      <div class="sm:col-span-6">
        <BaseInput :label="$t('board.form.url')" v-model="form.boardUrl" type="text" required :disabled="isEdit"
          :maxlength="BOARD_WRITE_LIMITS.boardUrl"
          :placeholder="$t('board.form.placeholder.url')" labelClass="text-base" pattern="[a-z0-9_-]*" />
      </div>

      <div class="sm:col-span-6">
        <BaseTextarea id="description" name="description" rows="3" v-model="form.description"
          :maxlength="BOARD_WRITE_LIMITS.description"
          :label="$t('board.form.description')" :placeholder="$t('board.form.placeholder.desc')"
          labelClass="text-base" />
      </div>

      <div class="sm:col-span-6">
        <BaseSelect
          id="board-visibility"
          v-model="visibility"
          :label="$t('board.form.visibility')"
          :options="visibilityOptions"
          aria-describedby="board-visibility-description"
        />
        <p id="board-visibility-description" class="mt-1 text-sm nv-text-muted">
          {{ visibilityDescription }}
        </p>
      </div>

      <div class="sm:col-span-6">
        <BaseCheckbox
          id="agent-use-yn"
          v-model="form.agentUseYn"
          :label="$t('board.form.agentUseYn')"
          :description="$t('board.form.agentUseYnDesc')"
          :disabled="!form.isPublic"
        />
      </div>

      <div v-if="form.agentUseYn" class="sm:col-span-6">
        <BaseTextarea
          id="guide-prompt"
          name="guidePrompt"
          rows="6"
          :maxlength="BOARD_WRITE_LIMITS.guidePrompt"
          v-model="form.guidePrompt"
          :label="$t('board.form.guidePrompt')"
          :placeholder="$t('board.form.placeholder.guidePrompt')"
          labelClass="text-base"
        />
      </div>
    </fieldset>

    <div class="flex justify-end space-x-3 items-center">
      <div v-if="!isEdit" class="flex items-center mr-2 text-sm"
        :class="canCreate ? 'nv-text-muted' : 'nv-form-error font-bold'" aria-live="polite">
        <span v-if="isBoardCreateCostLoading">{{ $t('board.form.createCostLoading') }}</span>
        <template v-else-if="boardCreateCostError">
          <span role="alert">{{ $t('board.form.createCostUnavailable') }}</span>
          <BaseButton type="button" variant="ghost" size="sm" class="ml-2" @click="loadBoardCreateCost">
            {{ $t('common.error.retry') }}
          </BaseButton>
        </template>
        <span v-else>{{ $t('board.form.cost') }}: {{ boardCreateCost }} P</span>
        <span class="mx-2 nv-text-subtle">|</span>
        <span>{{ $t('board.form.currentPoints') }}: {{ userPoints }} P</span>
      </div>
      <BaseButton type="button" variant="secondary" :disabled="isSubmitting" @click="handleCancel">
        {{ $t('common.cancel') }}
      </BaseButton>
      <BaseButton type="submit" variant="primary" :loading="isSubmitting" :disabled="isSubmitting || (!isEdit && !canCreate)">
        {{ isEdit ? $t('board.form.save') : $t('board.form.create') }}
      </BaseButton>
    </div>
  </form>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseTextarea from '@/components/common/ui/BaseTextarea.vue'
import BaseCheckbox from '@/components/common/ui/BaseCheckbox.vue'
import { useBoardCreationPolicy } from '@/features/board/form/useBoardCreationPolicy'
import { useBoardFormState, type BoardFormData } from '@/features/board/form/useBoardFormState'
import { useBoardFormSubmit } from '@/features/board/form/useBoardFormSubmit'
import { IMAGE_UPLOAD_ACCEPT } from '@/utils/imageUploadPolicy'

type BoardData = BoardFormData

const props = withDefaults(defineProps<{
  initialData?: BoardData
  isEdit?: boolean
  isSubmitting?: boolean
  error?: string
}>(), {
  initialData: () => ({
    boardName: '',
    boardUrl: '',
    description: '',
    iconUrl: '',
    sortOrder: 0,
    allowNsfw: false,
    isPublic: true,
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

const isEditMode = computed(() => props.isEdit)
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
} = useBoardFormSubmit({
  form,
  selectedFile,
  isEdit: () => props.isEdit,
  canCreate,
  boardCreateCost,
  emitSubmit: (data) => emit('submit', data),
})

const isSubmitting = computed(() => props.isSubmitting || localIsSubmitting.value)
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

    <div class="grid grid-cols-1 gap-y-8 gap-x-4 sm:grid-cols-6">

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
            <input id="icon-upload" type="file" @change="handleFileChange" :accept="IMAGE_UPLOAD_ACCEPT" class="hidden" />
            <span class="text-xs nv-text-subtle text-center block mt-1">{{ $t('board.form.iconImage')
            }}</span>
          </label>
        </div>

        <div class="flex-1">
          <BaseInput :label="$t('board.form.name')" v-model="form.boardName" type="text" required
            :placeholder="$t('board.form.placeholder.name')" labelClass="text-base" />
        </div>
      </div>

      <div class="sm:col-span-6">
        <BaseInput :label="$t('board.form.url')" v-model="form.boardUrl" type="text" required :disabled="isEdit"
          :placeholder="$t('board.form.placeholder.url')" labelClass="text-base" pattern="[a-z0-9_-]*" />
      </div>

      <div class="sm:col-span-6">
        <BaseTextarea id="description" name="description" rows="3" v-model="form.description"
          :label="$t('board.form.description')" :placeholder="$t('board.form.placeholder.desc')"
          labelClass="text-base" />
      </div>

      <div class="sm:col-span-6">
        <BaseCheckbox
          id="is-public"
          v-model="form.isPublic"
          :label="$t('board.form.isPublic')"
          :description="$t('board.form.isPublicDesc')"
        />
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
          maxlength="5000"
          v-model="form.guidePrompt"
          :label="$t('board.form.guidePrompt')"
          :placeholder="$t('board.form.placeholder.guidePrompt')"
          labelClass="text-base"
        />
      </div>
    </div>

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
      <BaseButton type="button" variant="secondary" @click="emit('cancel')">
        {{ $t('common.cancel') }}
      </BaseButton>
      <BaseButton type="submit" variant="primary" :loading="isSubmitting" :disabled="!isEdit && !canCreate">
        {{ isEdit ? $t('board.form.save') : $t('board.form.create') }}
      </BaseButton>
    </div>
  </form>
</template>

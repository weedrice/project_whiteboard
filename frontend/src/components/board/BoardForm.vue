<script setup lang="ts">
import { ref, watch, computed, onUnmounted } from 'vue'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseTextarea from '@/components/common/ui/BaseTextarea.vue'
import BaseCheckbox from '@/components/common/ui/BaseCheckbox.vue'
import { useI18n } from 'vue-i18n'
import { fileApi } from '@/api/file'
import { useToastStore } from '@/stores/toast'
import { useAuthStore } from '@/stores/auth'
import { useConfigStore } from '@/stores/config'
import { useFormSubmit } from '@/composables/useFormSubmit'
import { useErrorHandler } from '@/composables/useErrorHandler'
import { normalizeBoardUrlInput, validateRequiredBoardFields } from '@/utils/board'

interface BoardData {
  boardName: string
  boardUrl: string
  description: string
  iconUrl: string
  sortOrder: number
  allowNsfw: boolean
  isPublic: boolean
  agentUseYn: boolean
  guidePrompt: string
}

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

const { t } = useI18n()
const toastStore = useToastStore()
const authStore = useAuthStore()
const configStore = useConfigStore()
const { isSubmitting: localIsSubmitting, submit } = useFormSubmit()
const { handleError } = useErrorHandler()

const userPoints = computed(() => authStore.user?.points || 0)
const boardCreateCost = computed(() => {
  const cost = configStore.getConfig('POINT_BOARD_CREATE_COST')
  return cost ? parseInt(cost) : 500
})
const canCreate = computed(() => props.isEdit || userPoints.value >= boardCreateCost.value)

const form = ref<BoardData>({ ...props.initialData })
const selectedFile = ref<File | null>(null)
const previewImage = ref<string | null>(null)

// Watch for changes in initialData (e.g. when loading data in edit mode)
watch(() => props.initialData, (newData) => {
  form.value = { ...newData }
  if (!form.value.isPublic) {
    form.value.agentUseYn = false
  }
  if (newData.iconUrl) {
    previewImage.value = newData.iconUrl
  }
}, { deep: true, immediate: true })

watch(() => form.value.isPublic, (isPublic) => {
  if (!isPublic) {
    form.value.agentUseYn = false
  }
})

const handleFileChange = (event: Event) => {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  if (file) {
    selectedFile.value = file
    // 이전 preview URL 정리
    if (previewImage.value && previewImage.value.startsWith('blob:')) {
      URL.revokeObjectURL(previewImage.value)
    }
    previewImage.value = URL.createObjectURL(file)
  }
}

async function handleSubmit() {
  const requiredFieldValidation = validateRequiredBoardFields(form.value)
  if (!requiredFieldValidation.valid) {
    toastStore.addToast(t(requiredFieldValidation.messageKey), requiredFieldValidation.toastType)
    return
  }

  if (!props.isEdit && !canCreate.value) {
    toastStore.addToast(t('board.form.insufficientPoints', { cost: boardCreateCost.value }), 'error')
    return
  }

  await submit(async () => {
    let iconUrl = form.value.iconUrl

    try {
      if (selectedFile.value) {
        const { data } = await fileApi.uploadFile(selectedFile.value)
        if (data.success) {
          iconUrl = data.data.url
        }
      }

      emit('submit', {
        ...form.value,
        iconUrl,
        agentUseYn: form.value.isPublic ? form.value.agentUseYn : false
      })
    } catch (err) {
      handleError(err, t('board.form.uploadFailed'))
      throw err // Re-throw to prevent form submission
    }
  })
}

const isSubmitting = computed(() => props.isSubmitting || localIsSubmitting.value)

watch(() => form.value.boardUrl, (boardUrl) => {
  if (props.isEdit) return

  const normalizedBoardUrl = normalizeBoardUrlInput(boardUrl)
  if (boardUrl !== normalizedBoardUrl) {
    form.value.boardUrl = normalizedBoardUrl
  }
})

// Cleanup preview image URL
watch(previewImage, (newUrl, oldUrl) => {
  // 이전 URL 정리 (blob: URL인 경우만)
  if (oldUrl && oldUrl.startsWith('blob:')) {
    URL.revokeObjectURL(oldUrl)
  }
})

onUnmounted(() => {
  // 컴포넌트 unmount 시 preview URL 정리
  if (previewImage.value && previewImage.value.startsWith('blob:')) {
    URL.revokeObjectURL(previewImage.value)
  }
})
</script>

<template>
  <form @submit.prevent="handleSubmit"
    class="space-y-6 bg-white dark:bg-gray-800 shadow px-4 py-5 sm:rounded-lg sm:p-6 transition-colors duration-200">
    <div v-if="error" class="rounded-md bg-red-50 dark:bg-red-900/50 p-4">
      <div class="flex">
        <div class="ml-3">
          <h3 class="text-sm font-medium text-red-800 dark:text-red-400">{{ error }}</h3>
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
              class="h-16 w-16 rounded-md border border-gray-300 dark:border-gray-600 bg-gray-50 dark:bg-gray-700 flex items-center justify-center overflow-hidden hover:bg-gray-100 dark:hover:bg-gray-600 transition-colors duration-200">
              <img v-if="previewImage" :src="previewImage" :alt="$t('board.form.iconImage')" class="h-full w-full object-cover" />
              <svg v-else class="h-8 w-8 text-gray-400 dark:text-gray-500" fill="none" stroke="currentColor"
                viewBox="0 0 24 24" aria-hidden="true">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                  d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
              </svg>
            </div>
            <input id="icon-upload" type="file" @change="handleFileChange" accept="image/*" class="hidden" />
            <span class="text-xs text-gray-500 dark:text-gray-400 text-center block mt-1">{{ $t('board.form.iconImage')
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
          :placeholder="$t('board.form.placeholder.url')" labelClass="text-base" pattern="[a-z_]*" />
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
        :class="canCreate ? 'text-gray-600 dark:text-gray-400' : 'text-red-500 font-bold'">
        <span>{{ $t('board.form.cost') }}: {{ boardCreateCost }} P</span>
        <span class="mx-2 text-gray-300">|</span>
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

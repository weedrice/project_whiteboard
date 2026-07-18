<template>
  <AdminPanel max-width-class="w-full max-w-5xl">
    <template v-if="selectedBoard">
      <div class="space-y-7">
        <div>
          <h2 class="text-lg font-semibold nv-title">
            {{ selectedBoard.boardName }}
          </h2>
          <p class="mt-1 text-sm nv-text-subtle">
            {{ selectedBoard.boardUrl }}
          </p>
        </div>

        <AdminBoardFormFields
          v-model:board-name="boardNameModel"
          v-model:board-url="boardUrlModel"
          v-model:description="descriptionModel"
          v-model:agent-use-yn="agentUseYnModel"
          v-model:guide-prompt="guidePromptModel"
          layout="grid"
          :agent-disabled="!selectedBoard?.isPublic"
        >
          <template #after-identity>
            <div class="md:col-span-2 max-w-24">
              <BaseInput
                v-model="sortOrderModel"
                :label="t('common.sortOrder')"
                type="number"
                min="0"
              />
            </div>
            <div class="md:col-span-2 flex items-end justify-start md:justify-end">
              <button
                type="button"
                class="inline-flex items-center rounded-full px-3 py-1.5 text-sm font-semibold transition-colors"
                :class="form.isActive ? 'nv-status-success' : 'nv-status-danger'"
                @click="emit('toggle-status')"
              >
                {{ form.isActive ? t('common.active') : t('common.inactive') }}
              </button>
            </div>
          </template>
        </AdminBoardFormFields>

        <AdminBoardIconSection
          :icon-url="form.iconUrl"
          :set-file-input-ref="setFileInputRef"
          @upload="emit('icon-upload', $event)"
          @choose="emit('choose-icon')"
        />

        <AdminBoardManagerSection
          :loading="isBoardManagerLoading"
          :error="isBoardManagerError"
          :current-manager-label="currentManagerLabel"
          :is-assigning-manager="isAssigningManager"
          @open="emit('open-manager')"
          @retry="emit('retry-manager')"
        />

        <div class="flex justify-end">
          <BaseButton :disabled="isSubmitting || !hasUnsavedChanges" @click="emit('save')">
            {{ isSubmitting ? t('common.messages.saving') : t('common.saveChanges') }}
          </BaseButton>
        </div>
      </div>
    </template>

    <div v-else class="py-12 text-center text-sm nv-text-subtle">
      {{ t('common.noData') }}
    </div>
  </AdminPanel>
</template>

<script setup lang="ts">
import { computed, type ComponentPublicInstance } from 'vue'
import { useI18n } from 'vue-i18n'
import type { AdminBoard } from '@/types'
import type { AdminBoardEditorForm } from '@/features/admin/boards/useAdminBoardEditor'
import AdminPanel from '@/components/admin/AdminPanel.vue'
import AdminBoardFormFields from '@/components/admin/AdminBoardFormFields.vue'
import AdminBoardIconSection from '@/components/admin/AdminBoardIconSection.vue'
import AdminBoardManagerSection from '@/components/admin/AdminBoardManagerSection.vue'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'

const props = withDefaults(defineProps<{
  selectedBoard: AdminBoard | null
  form: AdminBoardEditorForm
  hasUnsavedChanges: boolean
  isSubmitting: boolean
  isBoardManagerLoading: boolean
  isBoardManagerError?: boolean
  isAssigningManager: boolean
  currentManagerLabel: string
  setFileInputRef: (element: Element | ComponentPublicInstance | null) => void
}>(), { isBoardManagerError: false })

const emit = defineEmits<{
  (event: 'toggle-status'): void
  (event: 'save'): void
  <K extends keyof AdminBoardEditorForm>(event: 'update-form', field: K, value: AdminBoardEditorForm[K]): void
  (event: 'icon-upload', uploadEvent: Event): void
  (event: 'choose-icon'): void
  (event: 'open-manager'): void
  (event: 'retry-manager'): void
}>()

const { t } = useI18n()

const boardNameModel = computed({
  get: () => props.form.boardName,
  set: (value: string) => emit('update-form', 'boardName', value),
})

const boardUrlModel = computed({
  get: () => props.form.boardUrl,
  set: (value: string) => emit('update-form', 'boardUrl', value),
})

const descriptionModel = computed({
  get: () => props.form.description,
  set: (value: string) => emit('update-form', 'description', value),
})

const agentUseYnModel = computed({
  get: () => props.form.agentUseYn,
  set: (value: boolean) => emit('update-form', 'agentUseYn', value),
})

const guidePromptModel = computed({
  get: () => props.form.guidePrompt,
  set: (value: string) => emit('update-form', 'guidePrompt', value),
})

const sortOrderModel = computed({
  get: () => props.form.sortOrder,
  set: (value: string) => emit('update-form', 'sortOrder', value),
})
</script>

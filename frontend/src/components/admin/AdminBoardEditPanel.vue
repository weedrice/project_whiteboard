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
          v-model:board-name="form.boardName"
          v-model:board-url="form.boardUrl"
          v-model:description="form.description"
          v-model:agent-use-yn="form.agentUseYn"
          v-model:guide-prompt="form.guidePrompt"
          layout="grid"
          :agent-disabled="!selectedBoard?.isPublic"
        >
          <template #after-identity>
            <div class="md:col-span-2 max-w-24">
              <BaseInput
                v-model="form.sortOrder"
                :label="t('common.sortOrder')"
                type="number"
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
          :current-manager-label="currentManagerLabel"
          :is-assigning-manager="isAssigningManager"
          @open="emit('open-manager')"
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
import type { ComponentPublicInstance } from 'vue'
import { useI18n } from 'vue-i18n'
import type { AdminBoard } from '@/types'
import type { AdminBoardEditorForm } from '@/composables/useAdminBoardEditor'
import AdminPanel from '@/components/admin/AdminPanel.vue'
import AdminBoardFormFields from '@/components/admin/AdminBoardFormFields.vue'
import AdminBoardIconSection from '@/components/admin/AdminBoardIconSection.vue'
import AdminBoardManagerSection from '@/components/admin/AdminBoardManagerSection.vue'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'

defineProps<{
  selectedBoard: AdminBoard | null
  form: AdminBoardEditorForm
  hasUnsavedChanges: boolean
  isSubmitting: boolean
  isBoardManagerLoading: boolean
  isAssigningManager: boolean
  currentManagerLabel: string
  setFileInputRef: (element: Element | ComponentPublicInstance | null) => void
}>()

const emit = defineEmits<{
  (event: 'toggle-status'): void
  (event: 'save'): void
  (event: 'icon-upload', uploadEvent: Event): void
  (event: 'choose-icon'): void
  (event: 'open-manager'): void
}>()

const { t } = useI18n()
</script>

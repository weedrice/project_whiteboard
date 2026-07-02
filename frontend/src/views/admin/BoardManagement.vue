<template>
  <AdminDataPage :title="$t('admin.boards.title')" :description="$t('admin.boards.description')">
    <template #actions>
      <div class="mt-4 sm:mt-0">
        <BaseButton @click="openCreateModal">
          {{ $t('admin.boards.addTitle') }}
        </BaseButton>
      </div>
    </template>

    <div class="mt-6 grid grid-cols-1 gap-6 xl:grid-cols-12">
      <section class="xl:col-span-4">
        <AdminBoardListPanel
          :boards="boards"
          :loading="loading"
          :selected-board-id="selectedBoardId"
          @update:boards="updateBoards"
          @select="selectBoard"
          @drag-end="handleDragEnd"
        />
      </section>

      <section class="xl:col-span-8">
        <AdminBoardEditPanel
          :selected-board="selectedBoard"
          :form="form"
          :has-unsaved-changes="hasUnsavedChanges"
          :is-submitting="isSubmitting"
          :is-board-manager-loading="isBoardManagerLoading"
          :is-assigning-manager="isAssigningManager"
          :current-manager-label="currentManagerLabel"
          :set-file-input-ref="setFileInputRef"
          @toggle-status="toggleBoardStatus"
          @save="handleSaveChanges"
          @update-form="updateBoardFormField"
          @icon-upload="handleFileUpload"
          @choose-icon="chooseIconFile"
          @open-manager="openManagerModal('single')"
        />
      </section>
    </div>

    <BaseModal :isOpen="isModalOpen" :title="$t('admin.boards.addTitle')" @close="closeModal">
      <div class="p-4 space-y-4">
        <AdminBoardFormFields
          v-model:board-name="createForm.boardName"
          v-model:board-url="createForm.boardUrl"
          v-model:description="createForm.description"
          v-model:agent-use-yn="createForm.agentUseYn"
          v-model:guide-prompt="createForm.guidePrompt"
          board-url-pattern="[a-z_]*"
        />
        <AdminModalActions class-name="pt-2">
          <BaseButton variant="secondary" @click="closeModal">{{ $t('common.cancel') }}</BaseButton>
          <BaseButton :disabled="isCreatingBoard" @click="handleCreateBoard">
            {{ isCreatingBoard ? $t('common.messages.saving') : $t('common.save') }}
          </BaseButton>
        </AdminModalActions>
      </div>
    </BaseModal>

    <UserSelectModal
      :isOpen="isManagerModalOpen"
      :title="$t('admin.boards.userSelectTitle')"
      :selectionMode="managerSelectionMode"
      @close="closeManagerModal"
      @confirm="confirmManagerSelection"
    />
  </AdminDataPage>
</template>

<script setup lang="ts">
import { type ComponentPublicInstance } from 'vue'
import { useAdmin } from '@/composables/useAdmin'
import { useAdminBoardEditor, type AdminBoardEditorForm } from '@/features/admin/boards/useAdminBoardEditor'
import { useAdminBoardCreateModal } from '@/features/admin/boards/useAdminBoardCreateModal'
import { useBoardManagerAssignment } from '@/features/admin/boards/useBoardManagerAssignment'
import { useBoardIconUpload } from '@/features/board/icons/useBoardIconUpload'
import type { AdminBoard } from '@/types'
import AdminDataPage from '@/components/admin/AdminDataPage.vue'
import AdminModalActions from '@/components/admin/AdminModalActions.vue'
import AdminBoardListPanel from '@/components/admin/AdminBoardListPanel.vue'
import AdminBoardEditPanel from '@/components/admin/AdminBoardEditPanel.vue'
import AdminBoardFormFields from '@/components/admin/AdminBoardFormFields.vue'
import BaseModal from '@/components/common/ui/BaseModal.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import UserSelectModal from '@/components/common/widgets/UserSelectModal.vue'
const {
  useAdminBoards,
  useCreateBoard,
  useUpdateBoard,
  useBoardManager,
  useUpdateBoardManager
} = useAdmin()

const { data: boardsData, isLoading: loading } = useAdminBoards()
const { mutateAsync: createBoard } = useCreateBoard()
const { mutateAsync: updateBoard } = useUpdateBoard()
const {
  closeModal,
  createForm,
  handleCreateBoard,
  isCreatingBoard,
  isModalOpen,
  openCreateModal
} = useAdminBoardCreateModal(createBoard)

const {
  boards,
  selectedBoardId,
  selectedBoard,
  form,
  hasUnsavedChanges,
  isSubmitting,
  handleDragEnd,
  toggleBoardStatus,
  selectBoard,
  handleSaveChanges
} = useAdminBoardEditor({ boardsData, updateBoard })

const {
  fileInput,
  handleFileUpload,
  chooseIconFile
} = useBoardIconUpload({
  setIconUrl: (iconUrl) => {
    form.iconUrl = iconUrl
  }
})

const setFileInputRef = (element: Element | ComponentPublicInstance | null) => {
  const target = element instanceof HTMLInputElement ? element : null
  fileInput.value = target
}

const updateBoards = (nextBoards: AdminBoard[]) => {
  boards.value = nextBoards
}

const updateBoardFormField = <K extends keyof AdminBoardEditorForm>(
  field: K,
  value: AdminBoardEditorForm[K],
) => {
  form[field] = value
}

const { data: boardManagerData, isLoading: isBoardManagerLoading } = useBoardManager(selectedBoardId)
const { mutateAsync: updateBoardManager } = useUpdateBoardManager()
const {
  isAssigningManager,
  isManagerModalOpen,
  managerSelectionMode,
  currentManagerLabel,
  openManagerModal,
  closeManagerModal,
  confirmManagerSelection
} = useBoardManagerAssignment({
  selectedBoard,
  boardManagerData,
  updateBoardManager
})
</script>

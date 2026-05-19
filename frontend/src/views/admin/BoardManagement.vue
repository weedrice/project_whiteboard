<template>
  <div class="space-y-6">
    <div class="sm:flex sm:items-center sm:justify-between">
      <div>
        <h1 class="text-2xl font-bold text-gray-900 dark:text-white">{{ $t('admin.boards.title') }}</h1>
        <p class="mt-2 text-sm text-gray-700 dark:text-gray-300">{{ $t('admin.boards.description') }}</p>
      </div>
      <div class="mt-4 sm:mt-0">
        <BaseButton @click="openCreateModal">
          {{ $t('admin.boards.addTitle') }}
        </BaseButton>
      </div>
    </div>

    <div class="grid grid-cols-1 gap-6 xl:grid-cols-12">
      <section class="xl:col-span-4">
        <div class="rounded-lg border border-gray-200 bg-white p-4 shadow dark:border-gray-700 dark:bg-gray-800">
          <h2 class="text-sm font-semibold text-gray-900 dark:text-white">{{ $t('common.board') }}</h2>

          <div v-if="loading" class="py-8 flex justify-center">
            <BaseSpinner />
          </div>

          <div v-else-if="boards.length === 0" class="py-8 text-sm text-gray-500 dark:text-gray-400 text-center">
            {{ $t('common.noData') }}
          </div>

          <draggable
            v-else
            v-model="boards"
            item-key="boardId"
            handle=".drag-handle"
            class="mt-3 space-y-2 max-h-[70vh] overflow-y-auto pr-1"
            @end="handleDragEnd"
          >
            <template #item="{ element: board }">
              <button
                type="button"
                @click="selectBoard(board)"
                class="w-full rounded-lg border px-3 py-3 text-left transition-colors"
                :class="selectedBoardId === board.boardId
                  ? 'border-indigo-500 bg-indigo-50 dark:border-indigo-400 dark:bg-indigo-900/30'
                  : 'border-gray-200 hover:bg-gray-50 dark:border-gray-700 dark:hover:bg-gray-700'"
              >
                <div class="flex items-start justify-between gap-2">
                  <p class="truncate text-sm font-semibold text-gray-900 dark:text-white flex items-center gap-2">
                    <GripVertical class="h-4 w-4 text-gray-400 dark:text-gray-500 drag-handle cursor-move" />
                    {{ board.boardName }}
                  </p>
                  <span
                    class="inline-flex rounded-full px-2 py-0.5 text-xs font-semibold"
                    :class="board.isActive
                      ? 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200'
                      : 'bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-200'"
                  >
                    {{ board.isActive ? $t('common.active') : $t('common.inactive') }}
                  </span>
                </div>
                <div class="mt-2 text-xs text-gray-500 dark:text-gray-400">
                  {{ $t('common.sortOrder') }}: {{ board.sortOrder }}
                </div>
              </button>
            </template>
          </draggable>
        </div>
      </section>

      <section class="xl:col-span-8">
        <div class="w-full max-w-5xl rounded-lg border border-gray-200 bg-white p-5 shadow dark:border-gray-700 dark:bg-gray-800">
          <template v-if="selectedBoard">
            <div class="space-y-7">
              <div>
                <h2 class="text-lg font-semibold text-gray-900 dark:text-white">
                  {{ selectedBoard.boardName }}
                </h2>
                <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
                  {{ selectedBoard.boardUrl }}
                </p>
              </div>

              <div class="grid grid-cols-1 gap-x-4 gap-y-3 md:grid-cols-12">
                <div class="md:col-span-4">
                  <BaseInput
                    v-model="form.boardName"
                    :label="$t('board.form.name')"
                    :placeholder="$t('board.form.placeholder.name')"
                  />
                </div>
                <div class="md:col-span-4">
                  <BaseInput
                    v-model="form.boardUrl"
                    :label="$t('board.form.url')"
                    :placeholder="$t('board.form.placeholder.url')"
                  />
                </div>
                <div class="md:col-span-2 max-w-24">
                  <BaseInput
                    v-model="form.sortOrder"
                    :label="$t('common.sortOrder')"
                    type="number"
                  />
                </div>
                <div class="md:col-span-2 flex items-end justify-start md:justify-end">
                  <button
                    type="button"
                    class="inline-flex items-center rounded-full px-3 py-1.5 text-sm font-semibold transition-colors"
                    :class="form.isActive
                      ? 'bg-green-100 text-green-800 hover:bg-green-200 dark:bg-green-900 dark:text-green-200 dark:hover:bg-green-800'
                      : 'bg-red-100 text-red-800 hover:bg-red-200 dark:bg-red-900 dark:text-red-200 dark:hover:bg-red-800'"
                    @click="toggleBoardStatus"
                  >
                    {{ form.isActive ? $t('common.active') : $t('common.inactive') }}
                  </button>
                </div>
              </div>

              <BaseTextarea
                v-model="form.description"
                :label="$t('board.form.description')"
                :placeholder="$t('board.form.placeholder.desc')"
                rows="4"
              />

              <BaseCheckbox
                v-model="form.agentUseYn"
                :label="$t('board.form.agentUseYn')"
                :description="$t('board.form.agentUseYnDesc')"
                :disabled="!selectedBoard?.isPublic"
              />

              <BaseTextarea
                v-if="form.agentUseYn"
                v-model="form.guidePrompt"
                :label="$t('board.form.guidePrompt')"
                :placeholder="$t('board.form.placeholder.guidePrompt')"
                rows="6"
                maxlength="5000"
              />

              <div class="space-y-2">
                <p class="text-xs sm:text-sm font-medium text-gray-700 dark:text-gray-200">
                  {{ $t('board.form.iconUrl') }}
                </p>
                <div class="flex items-start gap-4">
                  <div
                    class="h-20 w-20 flex-shrink-0 overflow-hidden rounded-full border border-gray-200 dark:border-gray-600 bg-white dark:bg-gray-700 flex items-center justify-center"
                  >
                    <img
                      v-if="form.iconUrl"
                      :src="form.iconUrl"
                      alt="icon"
                      class="h-full w-full object-contain"
                    />
                    <span v-else class="text-[11px] text-gray-400 dark:text-gray-500">N/A</span>
                  </div>

                  <div class="flex-1 min-w-0 space-y-2 pt-1">
                    <p
                      class="break-all text-sm leading-5"
                      :class="form.iconUrl ? 'text-gray-700 dark:text-gray-300' : 'text-gray-500 dark:text-gray-400'"
                    >
                      {{ form.iconUrl || '데이터가 없습니다.' }}
                    </p>

                    <div class="flex items-center">
                      <input
                        type="file"
                        ref="fileInput"
                        @change="handleFileUpload"
                        accept="image/*"
                        class="hidden"
                      />
                      <BaseButton type="button" variant="secondary" size="sm" @click="chooseIconFile">파일 선택</BaseButton>
                    </div>
                  </div>
                </div>
              </div>

              <div class="rounded-lg border border-gray-200 p-4 dark:border-gray-700">
                <h3 class="text-sm font-semibold text-gray-900 dark:text-white">게시판 관리자</h3>
                <div v-if="isBoardManagerLoading" class="mt-3 flex justify-center">
                  <BaseSpinner />
                </div>
                <div v-else class="mt-3 space-y-3">
                  <p class="text-sm text-gray-700 dark:text-gray-300">
                    {{ currentManagerLabel }}
                  </p>
                  <BaseButton @click="openManagerModal('single')" :disabled="isAssigningManager">
                    {{ isAssigningManager ? $t('common.messages.saving') : '관리자 선택' }}
                  </BaseButton>
                </div>
              </div>

              <div class="flex justify-end">
                <BaseButton @click="handleSaveChanges" :disabled="isSubmitting || !hasUnsavedChanges">
                  {{ isSubmitting ? $t('common.messages.saving') : $t('common.saveChanges') }}
                </BaseButton>
              </div>
            </div>
          </template>

          <div v-else class="py-12 text-center text-sm text-gray-500 dark:text-gray-400">
            {{ $t('common.noData') }}
          </div>
        </div>
      </section>
    </div>

    <BaseModal :isOpen="isModalOpen" :title="$t('admin.boards.addTitle')" @close="closeModal">
      <div class="p-4 space-y-4">
        <BaseInput v-model="createForm.boardName" :label="$t('board.form.name')" :placeholder="$t('board.form.placeholder.name')" />
        <BaseInput v-model="createForm.boardUrl" :label="$t('board.form.url')" :placeholder="$t('board.form.placeholder.url')" />
        <BaseTextarea v-model="createForm.description" :label="$t('board.form.description')" :placeholder="$t('board.form.placeholder.desc')" rows="3" />
        <BaseCheckbox
          v-model="createForm.agentUseYn"
          :label="$t('board.form.agentUseYn')"
          :description="$t('board.form.agentUseYnDesc')"
        />
        <BaseTextarea
          v-if="createForm.agentUseYn"
          v-model="createForm.guidePrompt"
          :label="$t('board.form.guidePrompt')"
          :placeholder="$t('board.form.placeholder.guidePrompt')"
          rows="5"
          maxlength="5000"
        />
        <div class="flex justify-end gap-3 pt-2">
          <BaseButton variant="secondary" @click="closeModal">{{ $t('common.cancel') }}</BaseButton>
          <BaseButton :disabled="isSubmitting" @click="handleCreateBoard">
            {{ isSubmitting ? $t('common.messages.saving') : $t('common.save') }}
          </BaseButton>
        </div>
      </div>
    </BaseModal>

    <UserSelectModal
      :isOpen="isManagerModalOpen"
      title="사용자 선택"
      :selectionMode="managerSelectionMode"
      @close="closeManagerModal"
      @confirm="confirmManagerSelection"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import draggable from 'vuedraggable'
import { GripVertical } from 'lucide-vue-next'
import { useAdmin } from '@/composables/useAdmin'
import { fileApi } from '@/api/file'
import BaseModal from '@/components/common/ui/BaseModal.vue'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseTextarea from '@/components/common/ui/BaseTextarea.vue'
import BaseCheckbox from '@/components/common/ui/BaseCheckbox.vue'
import BaseSpinner from '@/components/common/ui/BaseSpinner.vue'
import UserSelectModal from '@/components/common/widgets/UserSelectModal.vue'
import { useI18n } from 'vue-i18n'
import logger from '@/utils/logger'
import { useToastStore } from '@/stores/toast'
import { useConfirm } from '@/composables/useConfirm'
import type { AdminBoard } from '@/types'

const { t } = useI18n()
const toastStore = useToastStore()
const { confirm } = useConfirm()
const {
  useAdminBoards,
  useCreateBoard,
  useUpdateBoard,
  useBoardManager,
  useUpdateBoardManager
} = useAdmin()

const boards = ref<AdminBoard[]>([])
const originalBoardUrls = ref<Record<number, string>>({})
const modifiedBoardIds = ref<number[]>([])

const selectedBoardId = ref<number | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)
const isSubmitting = ref(false)
const isAssigningManager = ref(false)
const isSavingSortOrder = ref(false)

const isModalOpen = ref(false)
const isManagerModalOpen = ref(false)
const managerSelectionMode = ref<'single' | 'multiple'>('single')

const form = reactive({
  boardName: '',
  boardUrl: '',
  description: '',
  iconUrl: '',
  sortOrder: '0',
  isActive: true,
  agentUseYn: false,
  guidePrompt: ''
})

const createForm = reactive({
  boardName: '',
  boardUrl: '',
  description: '',
  iconUrl: '',
  agentUseYn: false,
  guidePrompt: ''
})

const { data: boardsData, isLoading: loading } = useAdminBoards()
const { mutateAsync: createBoard } = useCreateBoard()
const { mutateAsync: updateBoard } = useUpdateBoard()
const { data: boardManagerData, isLoading: isBoardManagerLoading } = useBoardManager(selectedBoardId)
const { mutateAsync: updateBoardManager } = useUpdateBoardManager()

const selectedBoard = computed<AdminBoard | null>(() => {
  if (!selectedBoardId.value) return null
  return boards.value.find((board) => board.boardId === selectedBoardId.value) || null
})

const normalizedSortOrder = computed(() => Number.parseInt(String(form.sortOrder || '0'), 10) || 0)

const isSelectedFormDirty = computed(() => {
  if (!selectedBoard.value) return false

  return (
    form.boardName !== selectedBoard.value.boardName ||
    form.boardUrl !== selectedBoard.value.boardUrl ||
    form.description !== (selectedBoard.value.description || '') ||
    form.iconUrl !== (selectedBoard.value.iconUrl || '') ||
    normalizedSortOrder.value !== selectedBoard.value.sortOrder ||
    form.isActive !== selectedBoard.value.isActive ||
    form.agentUseYn !== (selectedBoard.value.agentUseYn ?? false) ||
    form.guidePrompt !== (selectedBoard.value.guidePrompt || '')
  )
})

const hasUnsavedChanges = computed(() => isSelectedFormDirty.value || modifiedBoardIds.value.length > 0)

const currentManagerLabel = computed(() => {
  const manager = boardManagerData.value as {
    user?: { displayName?: string, loginId?: string }
  } | null

  if (manager?.user?.displayName || manager?.user?.loginId) {
    return `${manager.user.displayName || '-'} (${manager.user.loginId || '-'})`
  }

  if (selectedBoard.value?.adminDisplayName) {
    return selectedBoard.value.adminDisplayName
  }

  return t('common.noData')
})

watch(boardsData, (newData) => {
  const list = (newData || []) as AdminBoard[]
  const copied = JSON.parse(JSON.stringify(list)) as AdminBoard[]
  copied.sort((a, b) => a.sortOrder - b.sortOrder)

  boards.value = copied
  originalBoardUrls.value = Object.fromEntries(copied.map((board) => [board.boardId, board.boardUrl]))

  if (copied.length === 0) {
    selectedBoardId.value = null
    return
  }

  if (!selectedBoardId.value || !copied.some((board) => board.boardId === selectedBoardId.value)) {
    selectedBoardId.value = copied[0].boardId
  }
}, { immediate: true })

watch(selectedBoard, (board) => {
  if (!board) return
  form.boardName = board.boardName
  form.boardUrl = board.boardUrl
  form.description = board.description || ''
  form.iconUrl = board.iconUrl || ''
  form.sortOrder = String(board.sortOrder)
  form.isActive = board.isActive
  form.agentUseYn = board.isPublic ? (board.agentUseYn ?? false) : false
  form.guidePrompt = board.guidePrompt || ''
}, { immediate: true })

function markBoardModified(boardId: number) {
  if (!modifiedBoardIds.value.includes(boardId)) {
    modifiedBoardIds.value = [...modifiedBoardIds.value, boardId]
  }
}

function renumberSortOrder() {
  const changedBoardIds: number[] = []

  boards.value.forEach((board, index) => {
    const nextSortOrder = index + 1
    if (board.sortOrder !== nextSortOrder) {
      board.sortOrder = nextSortOrder
      markBoardModified(board.boardId)
      changedBoardIds.push(board.boardId)
    }
  })

  if (selectedBoard.value) {
    form.sortOrder = String(selectedBoard.value.sortOrder)
  }

  return changedBoardIds
}

async function saveBoardUpdates(boardIds: number[], showSuccessToast = true) {
  const uniqueBoardIds = [...new Set(boardIds)]
  if (uniqueBoardIds.length === 0) return

  const updates = uniqueBoardIds.map((boardId) => {
    const board = boards.value.find((item) => item.boardId === boardId)
    if (!board) return Promise.resolve()

    const requestBoardUrl = originalBoardUrls.value[board.boardId] || board.boardUrl

    return updateBoard({
      boardUrl: requestBoardUrl,
      data: {
        boardName: board.boardName,
        boardUrl: board.boardUrl,
        description: board.description || '',
        iconUrl: board.iconUrl || '',
        allowNsfw: board.allowNsfw,
        sortOrder: board.sortOrder,
        isActive: board.isActive,
        agentUseYn: board.agentUseYn ?? false,
        guidePrompt: board.guidePrompt || ''
      }
    }).then(() => {
      originalBoardUrls.value[board.boardId] = board.boardUrl
    })
  })

  await Promise.all(updates)

  modifiedBoardIds.value = modifiedBoardIds.value.filter((boardId) => !uniqueBoardIds.includes(boardId))

  if (showSuccessToast) {
    toastStore.addToast(t('common.messages.saveSuccess'), 'success')
  }
}

async function handleDragEnd() {
  if (isSavingSortOrder.value) return

  const changedBoardIds = renumberSortOrder()
  if (changedBoardIds.length === 0) return

  isSavingSortOrder.value = true
  try {
    await saveBoardUpdates(changedBoardIds, false)
  } catch {
    // Error handled globally
  } finally {
    isSavingSortOrder.value = false
  }
}

function toggleBoardStatus() {
  form.isActive = !form.isActive
}

async function selectBoard(board: AdminBoard) {
  if (selectedBoardId.value === board.boardId) return

  if (hasUnsavedChanges.value) {
    const isConfirmed = await confirm('저장하지 않은 변경사항이 있습니다. 이동하시겠습니까?')
    if (!isConfirmed) return
  }

  selectedBoardId.value = board.boardId
}

async function handleFileUpload(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return

  try {
    const { data } = await fileApi.uploadFile(file)

    if (data.success) {
      form.iconUrl = data.data.url
    }
  } catch (error: unknown) {
    logger.error('Failed to upload board icon:', error)
    toastStore.addToast(t('common.messages.error'), 'error')
  }
}

function chooseIconFile() {
  fileInput.value?.click()
}

function applySelectedBoardForm() {
  if (!selectedBoard.value) return

  const board = selectedBoard.value

  if (board.boardName !== form.boardName) {
    board.boardName = form.boardName
    markBoardModified(board.boardId)
  }

  if (board.boardUrl !== form.boardUrl) {
    board.boardUrl = form.boardUrl
    markBoardModified(board.boardId)
  }

  if ((board.description || '') !== form.description) {
    board.description = form.description
    markBoardModified(board.boardId)
  }

  if ((board.iconUrl || '') !== form.iconUrl) {
    board.iconUrl = form.iconUrl
    markBoardModified(board.boardId)
  }

  if (board.isActive !== form.isActive) {
    board.isActive = form.isActive
    markBoardModified(board.boardId)
  }

  const nextAgentUseYn = board.isPublic ? form.agentUseYn : false
  if ((board.agentUseYn ?? false) !== nextAgentUseYn) {
    board.agentUseYn = nextAgentUseYn
    markBoardModified(board.boardId)
  }

  if ((board.guidePrompt || '') !== form.guidePrompt) {
    board.guidePrompt = form.guidePrompt
    markBoardModified(board.boardId)
  }

  const targetPosition = Math.max(1, Math.min(normalizedSortOrder.value, boards.value.length))
  form.sortOrder = String(targetPosition)
  const currentIndex = boards.value.findIndex((item) => item.boardId === board.boardId)

  if (currentIndex >= 0 && currentIndex !== targetPosition - 1) {
    const [movedBoard] = boards.value.splice(currentIndex, 1)
    boards.value.splice(targetPosition - 1, 0, movedBoard)
    renumberSortOrder()
  }
}

async function handleSaveChanges() {
  if (!selectedBoard.value) return

  if (!form.boardName || !form.boardUrl) {
    toastStore.addToast(t('board.writePost.validation'), 'warning')
    return
  }

  applySelectedBoardForm()

  if (modifiedBoardIds.value.length === 0) {
    return
  }

  isSubmitting.value = true
  try {
    await saveBoardUpdates(modifiedBoardIds.value, true)
  } catch {
    // Error handled globally
  } finally {
    isSubmitting.value = false
  }
}

function openCreateModal() {
  createForm.boardName = ''
  createForm.boardUrl = ''
  createForm.description = ''
  createForm.iconUrl = ''
  createForm.agentUseYn = false
  createForm.guidePrompt = ''
  isModalOpen.value = true
}

function closeModal() {
  isModalOpen.value = false
}

async function handleCreateBoard() {
  if (!createForm.boardName || !createForm.boardUrl) {
    toastStore.addToast(t('board.writePost.validation'), 'warning')
    return
  }

  isSubmitting.value = true
  try {
    await createBoard({ ...createForm })
    toastStore.addToast(t('admin.boards.messages.created'), 'success')
    closeModal()
  } catch {
    // Error handled globally
  } finally {
    isSubmitting.value = false
  }
}

function openManagerModal(mode: 'single' | 'multiple' = 'single') {
  managerSelectionMode.value = mode
  isManagerModalOpen.value = true
}

function closeManagerModal() {
  isManagerModalOpen.value = false
}

async function confirmManagerSelection(users: Array<{ loginId: string; displayName?: string }>) {
  if (!selectedBoard.value || users.length === 0) return

  isAssigningManager.value = true
  try {
    const selectedUser = users[0]
    await updateBoardManager({
      boardId: selectedBoard.value.boardId,
      data: { loginId: selectedUser.loginId }
    })

    selectedBoard.value.adminDisplayName = selectedUser.displayName
    closeManagerModal()
    toastStore.addToast(t('admin.admins.messages.added'), 'success')
  } catch {
    // Error handled globally
  } finally {
    isAssigningManager.value = false
  }
}
</script>

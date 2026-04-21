<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { boardApi } from '@/api/board'
import CategoryManager from '@/components/board/CategoryManager.vue'
import BoardForm from '@/components/board/BoardForm.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import UserSelectModal from '@/components/common/widgets/UserSelectModal.vue'
import { useI18n } from 'vue-i18n'
import { useToastStore } from '@/stores/toast'
import { useConfirm } from '@/composables/useConfirm'
import { useFormSubmit } from '@/composables/useFormSubmit'
import { useErrorHandler } from '@/composables/useErrorHandler'
import { useBoard } from '@/composables/useBoard'
import type { BoardDetail, BoardUpdateData, User } from '@/types'

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

const { t } = useI18n()
const toastStore = useToastStore()
const { confirm } = useConfirm()

const route = useRoute()
const router = useRouter()
const boardUrl = route.params.boardUrl as string

const form = ref({
  boardName: '',
  boardUrl: '',
  description: '',
  iconUrl: '',
  sortOrder: 0,
  allowNsfw: false,
  isPublic: true,
  agentUseYn: false,
  guidePrompt: ''
})

const isLoading = ref(true)
const { isSubmitting, submit } = useFormSubmit()
const { handleError } = useErrorHandler()
const { useUpdateBoard, useDeleteBoard, useTransferBoardManager } = useBoard()
const { mutateAsync: updateBoard } = useUpdateBoard()
const { mutateAsync: deleteBoard } = useDeleteBoard()
const { mutateAsync: transferBoardManager } = useTransferBoardManager()
const error = ref('')
const isManagerModalOpen = ref(false)
const isTransferringManager = ref(false)
const currentManagerLabel = ref('')

async function fetchBoard() {
  isLoading.value = true
  try {
    const { data } = await boardApi.getBoard(boardUrl)
    if (data.success) {
      const board = data.data as BoardDetail
      form.value = {
        boardName: board.boardName,
        boardUrl: board.boardUrl,
        description: board.description || '',
        iconUrl: board.iconUrl || '',
        sortOrder: board.sortOrder || 0,
        allowNsfw: board.allowNsfw || false,
        isPublic: board.isPublic ?? true,
        agentUseYn: board.agentUseYn ?? false,
        guidePrompt: board.guidePrompt || ''
      }
      currentManagerLabel.value = board.adminDisplayName || t('common.noData')
    }
  } catch (err: unknown) {
    handleError(err, t('board.loadFailed'))
    router.push(`/board/${boardUrl}`)
  } finally {
    isLoading.value = false
  }
}

async function handleUpdate(formData: BoardData) {
  error.value = ''

  await submit(async () => {
    try {
      const board = await updateBoard({ boardUrl, data: formData as BoardUpdateData })
      toastStore.addToast(t('board.form.successUpdate'), 'success')
      router.push(`/board/${board.boardUrl}`)
    } catch (err: unknown) {
      error.value = t('board.form.updateFailed')
      handleError(err, t('board.form.updateFailed'))
      throw err
    }
  })
}

async function handleDelete() {
  const isConfirmed = await confirm(t('board.form.deleteConfirm'))
  if (!isConfirmed) return

  try {
    await deleteBoard(boardUrl)
    toastStore.addToast(t('board.form.successDelete'), 'success')
    router.push('/')
  } catch (err: unknown) {
    handleError(err, t('board.form.deleteFailed'))
  }
}

function openManagerModal() {
  isManagerModalOpen.value = true
}

function closeManagerModal() {
  isManagerModalOpen.value = false
}

async function confirmManagerSelection(users: User[]) {
  if (users.length === 0) return

  const selectedUser = users[0]

  isTransferringManager.value = true
  try {
    const updatedBoard = await transferBoardManager({
      boardUrl,
      loginId: selectedUser.loginId
    })
    currentManagerLabel.value = updatedBoard.adminDisplayName || `${selectedUser.displayName} (${selectedUser.loginId})`
    closeManagerModal()
    toastStore.addToast(t('common.messages.saveSuccess'), 'success')
  } catch (err: unknown) {
    handleError(err, t('common.messages.saveFailed'))
  } finally {
    isTransferringManager.value = false
  }
}

onMounted(fetchBoard)
</script>

<template>
  <div class="max-w-3xl mx-auto">
    <div v-if="isLoading" class="text-center py-10">
      <div class="animate-spin rounded-full h-10 w-10 border-b-2 border-indigo-600 mx-auto"></div>
    </div>

    <div v-else class="bg-white dark:bg-gray-800 shadow sm:rounded-lg overflow-hidden">
      <!-- Header -->
      <div class="px-4 py-5 sm:px-6 border-b border-gray-200 dark:border-gray-700 flex justify-between items-center">
        <div>
          <h3 class="text-lg leading-6 font-medium text-gray-900 dark:text-white">{{ $t('board.form.editTitle') }}</h3>
          <p class="mt-1 max-w-2xl text-sm text-gray-500 dark:text-gray-400">{{ $t('board.form.editDesc') }}</p>
        </div>
        <BaseButton type="button" @click="router.back()" variant="secondary">
          {{ $t('common.back') }}
        </BaseButton>
      </div>

      <div class="px-4 py-5 sm:p-6 space-y-6">
        <!-- Board Form -->
        <BoardForm :initialData="form" :isEdit="true" :isSubmitting="isSubmitting" :error="error" @submit="handleUpdate"
          @cancel="router.back()" />

        <hr class="border-gray-200 dark:border-gray-700" />

        <div class="rounded-lg border border-gray-200 p-4 dark:border-gray-700">
          <div class="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <h4 class="text-sm font-semibold text-gray-900 dark:text-white">{{ $t('common.admin') }}</h4>
              <p class="mt-1 text-sm text-gray-600 dark:text-gray-300">{{ currentManagerLabel }}</p>
            </div>
            <BaseButton type="button" @click="openManagerModal" :disabled="isTransferringManager">
              {{ isTransferringManager ? $t('common.messages.saving') : $t('common.manage') }}
            </BaseButton>
          </div>
        </div>

        <hr class="border-gray-200 dark:border-gray-700" />

        <!-- Category Manager -->
        <div class="py-6">
          <CategoryManager :boardUrl="boardUrl" />
        </div>

        <hr class="border-gray-200 dark:border-gray-700" />

        <!-- Delete Board (Moved to bottom right) -->
        <div class="flex justify-end">
          <BaseButton type="button" @click="handleDelete" variant="danger">
            {{ $t('board.form.delete') }}
          </BaseButton>
        </div>
      </div>
    </div>

    <UserSelectModal
      :isOpen="isManagerModalOpen"
      selectionMode="single"
      @close="closeManagerModal"
      @confirm="confirmManagerSelection"
    />
  </div>
</template>

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

    <!-- Board List -->
    <div class="mb-20">
      <div
        class="bg-white dark:bg-gray-800 shadow overflow-hidden sm:rounded-lg border border-gray-200 dark:border-gray-700">
        <div class="overflow-x-auto">
          <table class="min-w-full table-fixed divide-y divide-gray-200 dark:divide-gray-700">
            <colgroup>
              <col v-for="col in columns" :key="col.key" :style="{ width: col.width || 'auto' }" />
            </colgroup>
            <thead class="bg-gray-50 dark:bg-gray-700">
              <tr>
                <th v-for="col in columns" :key="col.key" scope="col"
                  class="px-3 sm:px-6 py-2 sm:py-3 text-[10px] sm:text-xs font-medium text-gray-500 dark:text-gray-300 uppercase tracking-wider whitespace-nowrap"
                  :class="alignClass(col.align)">
                  {{ col.label }}
                </th>
              </tr>
            </thead>

            <tbody v-if="loading">
              <tr>
                <td :colspan="columns.length" class="px-6 py-10 text-center">
                  <div class="flex justify-center">
                    <BaseSpinner />
                  </div>
                </td>
              </tr>
            </tbody>
            <tbody v-else-if="boards.length === 0">
              <tr>
                <td :colspan="columns.length" class="px-6 py-10 text-center text-gray-500 dark:text-gray-400">
                  {{ $t('common.noData') }}
                </td>
              </tr>
            </tbody>

            <draggable v-else v-model="boards" tag="tbody" item-key="boardId" handle=".drag-handle" @end="handleDragEnd"
              class="bg-white dark:bg-gray-800 divide-y divide-gray-200 dark:divide-gray-700">
              <template #item="{ element: board }">
                <tr class="hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors duration-150">
                  <td
                    class="px-3 sm:px-6 py-3 sm:py-4 whitespace-nowrap text-xs sm:text-sm text-gray-900 dark:text-white align-middle">
                    <div class="flex items-center font-medium">
                      <GripVertical class="h-4 w-4 text-gray-400 dark:text-gray-500 mr-2 cursor-move drag-handle" />
                      {{ board.boardName }}
                    </div>
                  </td>
                  <td
                    class="px-3 sm:px-6 py-3 sm:py-4 whitespace-nowrap text-xs sm:text-sm text-gray-900 dark:text-white align-middle">
                    {{ board.boardUrl }}
                  </td>
                  <td
                    class="px-3 sm:px-6 py-3 sm:py-4 whitespace-nowrap text-xs sm:text-sm text-gray-900 dark:text-white align-middle">
                    <BaseInput v-model="board.description" @input="handleInputChange(board)" hideLabel
                      inputClass="block w-full border-0 p-0 text-gray-500 dark:text-gray-400 placeholder-gray-500 dark:placeholder-gray-500 focus:ring-0 sm:text-sm truncate bg-transparent shadow-none" />
                  </td>
                  <td
                    class="px-3 sm:px-6 py-3 sm:py-4 whitespace-nowrap text-xs sm:text-sm text-gray-900 dark:text-white align-middle text-center">
                    <button @click="board.isActive = !board.isActive; handleInputChange(board)"
                      class="px-2 inline-flex text-xs leading-5 font-semibold rounded-full cursor-pointer"
                      :class="board.isActive ? 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200' : 'bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-200'">
                      {{ board.isActive ? t('common.active') : t('common.inactive') }}
                    </button>
                  </td>
                  <td
                    class="px-3 sm:px-6 py-3 sm:py-4 whitespace-nowrap text-xs sm:text-sm text-gray-900 dark:text-white align-middle text-center">
                    <BaseInput v-model="board.sortOrder" type="number" @change="handleSortOrderChange(board)" hideLabel
                      inputClass="block w-full border-0 p-0 text-gray-500 dark:text-gray-400 placeholder-gray-500 dark:placeholder-gray-500 focus:ring-0 sm:text-sm text-center bg-transparent shadow-none" />
                  </td>
                </tr>
              </template>
            </draggable>
          </table>
        </div>
      </div>
    </div>

    <!-- Floating Save Button -->
    <div v-if="modifiedBoards.size > 0" class="fixed bottom-8 right-8 z-50">
      <BaseButton @click="handleSaveAll" :disabled="isSubmitting"
        class="rounded-full shadow-lg transition-all duration-200 transform hover:scale-105 px-6 py-3">
        <Save class="-ml-1 mr-3 h-5 w-5" />
        {{ isSubmitting ? $t('common.messages.saving') : $t('common.saveChanges') }}
      </BaseButton>
    </div>

    <!-- Create/Edit Modal -->
    <BaseModal :isOpen="isModalOpen" :title="isEditMode ? $t('admin.boards.editTitle') : $t('admin.boards.addTitle')"
      @close="closeModal">
      <div class="p-4 space-y-4">
        <BaseInput v-model="form.boardName" :label="$t('board.form.name')"
          :placeholder="$t('board.form.placeholder.name')" />
        <BaseInput v-model="form.boardUrl" :label="$t('board.form.url')" :placeholder="$t('board.form.placeholder.url')"
          :disabled="isEditMode" />
        <div>
          <BaseTextarea v-model="form.description" :label="$t('board.form.description')"
            :placeholder="$t('board.form.placeholder.desc')" rows="3" />
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">{{ $t('board.form.iconUrl')
          }}</label>
          <div class="flex items-center space-x-4">
            <div v-if="form.iconUrl"
              class="flex-shrink-0 border border-gray-200 dark:border-gray-600 rounded-full overflow-hidden h-12 w-12">
              <img :src="form.iconUrl" alt="Icon Preview"
                class="h-full w-full object-contain bg-white dark:bg-gray-700">
            </div>
            <input type="file" ref="fileInput" @change="handleFileUpload" accept="image/*" class="block w-full text-sm text-gray-500 dark:text-gray-400
                file:mr-4 file:py-2 file:px-4
                file:rounded-full file:border-0
                file:text-sm file:font-semibold
                file:bg-indigo-50 dark:file:bg-indigo-900 file:text-indigo-700 dark:file:text-indigo-300
                hover:file:bg-indigo-100 dark:hover:file:bg-indigo-800" />
          </div>
          <input type="hidden" v-model="form.iconUrl">
        </div>

        <div class="flex justify-end space-x-3 pt-4">
          <BaseButton @click="closeModal" variant="secondary">{{ $t('common.cancel') }}</BaseButton>
          <BaseButton @click="handleSubmit" :disabled="isSubmitting">
            {{ isSubmitting ? $t('common.messages.saving') : $t('common.save') }}
          </BaseButton>
        </div>
      </div>
    </BaseModal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, watch, computed } from 'vue'
import { useAdmin } from '@/composables/useAdmin'
import api from '@/api'
import BaseModal from '@/components/common/ui/BaseModal.vue'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseTextarea from '@/components/common/ui/BaseTextarea.vue'
import BaseSpinner from '@/components/common/ui/BaseSpinner.vue'
import { useI18n } from 'vue-i18n'
import { Save, GripVertical } from 'lucide-vue-next'
import draggable from 'vuedraggable'
import logger from '@/utils/logger'
import { useToastStore } from '@/stores/toast'
import { useConfirm } from '@/composables/useConfirm'

const { t } = useI18n()
const toastStore = useToastStore()
const { confirm } = useConfirm()
const { useAdminBoards, useCreateBoard, useUpdateBoard } = useAdmin()

import type { Board } from '@/types'

const boards = ref<Board[]>([])
const isModalOpen = ref(false)
const isEditMode = ref(false)
const isSubmitting = ref(false)
const fileInput = ref(null)

const { data: boardsData, isLoading: loading } = useAdminBoards()
const { mutateAsync: createBoard } = useCreateBoard()
const { mutateAsync: updateBoard } = useUpdateBoard()

watch(boardsData, (newData) => {
  if (newData) {
    boards.value = JSON.parse(JSON.stringify(newData))
  }
}, { immediate: true })

const form = reactive({
  boardName: '',
  boardUrl: '',
  description: '',
  iconUrl: ''
})

interface TableColumn {
  key: string
  label: string
  width?: string
  align?: 'left' | 'center' | 'right'
}

const columns = computed<TableColumn[]>(() => [
  { key: 'boardName', label: t('common.board') + ' ' + t('common.name'), width: '20%' },
  { key: 'boardUrl', label: t('common.url'), width: '20%' },
  { key: 'description', label: t('common.description'), width: '30%' },
  { key: 'isActive', label: t('common.status'), align: 'center', width: '15%' },
  { key: 'sortOrder', label: t('common.sortOrder'), align: 'center', width: '15%' }
])

const alignClass = (align?: string) => {
  switch (align) {
    case 'center': return 'text-center'
    case 'right': return 'text-right'
    default: return 'text-left'
  }
}

async function handleFileUpload(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return

  const formData = new FormData()
  formData.append('file', file)

  try {
    const { data } = await api.post('/files/upload', formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })

    if (data.success) {
      form.iconUrl = data.data.url
    }
  } catch (error: unknown) {
    logger.error('Failed to upload file:', error)
    toastStore.addToast(t('common.messages.error'), 'error')
  }
}

function openCreateModal() {
  isEditMode.value = false
  form.boardName = ''
  form.boardUrl = ''
  form.description = ''
  form.iconUrl = ''
  isModalOpen.value = true
}

function openEditModal(board: Board) {
  isEditMode.value = true
  form.boardName = board.boardName
  form.boardUrl = board.boardUrl || ''
  form.description = board.description || ''
  form.iconUrl = board.iconUrl || ''
  isModalOpen.value = true
}

function closeModal() {
  isModalOpen.value = false
}

async function handleSubmit() {
  if (!form.boardName || !form.boardUrl) {
    toastStore.addToast(t('board.writePost.validation'), 'warning')
    return
  }

  isSubmitting.value = true
  try {
    const payload = { ...form }

    if (isEditMode.value) {
      await updateBoard({ boardUrl: form.boardUrl, data: payload })
    } else {
      await createBoard(payload)
    }

    toastStore.addToast(isEditMode.value ? t('admin.boards.messages.updated') : t('admin.boards.messages.created'), 'success')
    closeModal()
  } catch {
    // Error handled globally
  } finally {
    isSubmitting.value = false
  }
}

const modifiedBoards = ref(new Set<number>())

function handleInputChange(board: Board) {
  modifiedBoards.value.add(board.boardId)
}

function handleDragEnd() {
  boards.value.forEach((board, index) => {
    const newSortOrder = index + 1
    if (board.sortOrder !== newSortOrder) {
      board.sortOrder = newSortOrder
      handleInputChange(board)
    }
  })
}

function handleSortOrderChange(board: Board) {
  handleInputChange(board)
  boards.value.sort((a, b) => a.sortOrder - b.sortOrder)
}

async function handleSaveAll() {
  if (modifiedBoards.value.size === 0) return

  const isConfirmed = await confirm(t('common.messages.save'))
  if (!isConfirmed) return

  isSubmitting.value = true
  try {
    const promises = Array.from(modifiedBoards.value).map(boardId => {
      const board = boards.value.find(b => b.boardId === boardId)
      if (!board) return Promise.resolve()

      return updateBoard({
        boardUrl: board.boardUrl || '',
        data: {
          boardName: board.boardName,
          description: board.description || '',
          iconUrl: board.iconUrl || '',
          allowNsfw: board.allowNsfw || false,
          sortOrder: typeof board.sortOrder === 'string' ? parseInt(board.sortOrder) : (board.sortOrder || 0),
          isActive: board.isActive
        }
      })
    })

    await Promise.all(promises)
    toastStore.addToast(t('common.messages.saveSuccess'), 'success')
    modifiedBoards.value.clear()
  } catch {
    // Error handled globally
  } finally {
    isSubmitting.value = false
  }
}
</script>

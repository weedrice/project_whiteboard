<template>
  <div class="space-y-6">
    <div class="sm:flex sm:items-center sm:justify-between">
      <div>
        <h1 class="text-2xl font-bold text-gray-900 dark:text-white">{{ $t('admin.boards.title') }}</h1>
        <p class="mt-2 text-sm text-gray-700 dark:text-gray-300">{{ $t('admin.boards.description') }}</p>
      </div>
      <div class="mt-4 sm:mt-0">
        <button
          @click="openCreateModal"
          class="inline-flex items-center justify-center rounded-md border border-transparent bg-indigo-600 px-4 py-2 text-sm font-medium text-white shadow-sm hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2 sm:w-auto"
        >
          {{ $t('admin.boards.addTitle') }}
        </button>
      </div>
    </div>

    <!-- Board List -->
    <div class="bg-white dark:bg-gray-800 shadow overflow-hidden sm:rounded-lg mb-20 border border-gray-200 dark:border-gray-700">
      <table class="min-w-full divide-y divide-gray-200 dark:divide-gray-700">
        <thead class="bg-gray-50 dark:bg-gray-700">
          <tr>
            <th scope="col" class="px-6 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-300 uppercase tracking-wider">
              {{ $t('common.board') + ' ' + $t('common.name') }}
            </th>
            <th scope="col" class="px-6 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-300 uppercase tracking-wider">
              {{ $t('common.url') }}
            </th>
            <th scope="col" class="px-6 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-300 uppercase tracking-wider">
              {{ $t('common.description') }}
            </th>
            <th scope="col" class="px-6 py-3 text-center text-xs font-medium text-gray-500 dark:text-gray-300 uppercase tracking-wider">
              {{ $t('common.status') }}
            </th>
            <th scope="col" class="px-6 py-3 text-center text-xs font-medium text-gray-500 dark:text-gray-300 uppercase tracking-wider">
              {{ $t('common.sortOrder') }}
            </th>
          </tr>
        </thead>
        <draggable 
          v-model="boards" 
          tag="tbody" 
          item-key="boardId"
          handle=".drag-handle"
          @end="handleDragEnd"
          class="bg-white dark:bg-gray-800 divide-y divide-gray-200 dark:divide-gray-700"
        >
          <template #item="{ element: board }">
            <tr v-if="loading">
              <td colspan="5" class="px-6 py-4 text-center text-sm text-gray-500 dark:text-gray-400">
                {{ t('common.loading') }}
              </td>
            </tr>
            <tr v-else-if="boards.length === 0">
              <td colspan="5" class="px-6 py-4 text-center text-sm text-gray-500 dark:text-gray-400">
                {{ $t('common.noData') }}
              </td>
            </tr>
            <tr v-else :key="board.boardId">
              <td class="px-6 py-4 whitespace-nowrap text-sm font-medium text-gray-900 dark:text-white flex items-center">
                <GripVertical class="h-4 w-4 text-gray-400 dark:text-gray-500 mr-2 cursor-move drag-handle" />
                {{ board.boardName }}
              </td>
              <td class="px-6 py-4 whitespace-nowrap text-sm text-gray-500 dark:text-gray-400">
                {{ board.boardUrl }}
              </td>
              <td class="px-6 py-4 text-sm text-gray-500 dark:text-gray-400 max-w-xs">
                <input 
                  v-model="board.description" 
                  @input="handleInputChange(board)"
                  class="block w-full border-0 p-0 text-gray-500 dark:text-gray-400 placeholder-gray-500 dark:placeholder-gray-500 focus:ring-0 sm:text-sm truncate bg-transparent"
                />
              </td>
              <td class="px-6 py-4 whitespace-nowrap text-center text-sm">
                <button 
                  @click="board.isActive = !board.isActive; handleInputChange(board)"
                  class="px-2 inline-flex text-xs leading-5 font-semibold rounded-full cursor-pointer"
                  :class="board.isActive ? 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200' : 'bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-200'"
                >
                  {{ board.isActive ? t('common.active') : t('common.inactive') }}
                </button>
              </td>
              <td class="px-6 py-4 whitespace-nowrap text-center text-sm text-gray-500 dark:text-gray-400">
                <input 
                  v-model="board.sortOrder" 
                  type="number"
                  @change="handleSortOrderChange(board)"
                  class="block w-full border-0 p-0 text-gray-500 dark:text-gray-400 placeholder-gray-500 dark:placeholder-gray-500 focus:ring-0 sm:text-sm text-center bg-transparent"
                />
              </td>
            </tr>
          </template>
        </draggable>
      </table>
    </div>

    <!-- Floating Save Button -->
    <div v-if="modifiedBoards.size > 0" class="fixed bottom-8 right-8 z-50">
      <button
        @click="handleSaveAll"
        :disabled="isSubmitting"
        class="inline-flex items-center px-6 py-3 border border-transparent shadow-lg text-base font-medium rounded-full text-white bg-indigo-600 hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500 transition-all duration-200 transform hover:scale-105"
      >
        <Save class="-ml-1 mr-3 h-5 w-5" />
        {{ isSubmitting ? $t('common.messages.saving') : $t('common.saveChanges') }}
      </button>
    </div>

    <!-- Create/Edit Modal -->
    <BaseModal
      :isOpen="isModalOpen"
      :title="isEditMode ? $t('admin.boards.editTitle') : $t('admin.boards.addTitle')"
      @close="closeModal"
    >
      <div class="p-4 space-y-4">
        <BaseInput
          v-model="form.boardName"
          :label="$t('board.form.name')"
          :placeholder="$t('board.form.placeholder.name')"
        />
        <BaseInput
          v-model="form.boardUrl"
          :label="$t('board.form.url')"
          :placeholder="$t('board.form.placeholder.url')"
          :disabled="isEditMode"
        />
        <div>
          <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">{{ $t('board.form.description') }}</label>
          <textarea
            v-model="form.description"
            rows="3"
            class="shadow-sm focus:ring-indigo-500 focus:border-indigo-500 block w-full sm:text-sm border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-700 text-gray-900 dark:text-white placeholder-gray-500 dark:placeholder-gray-400"
            :placeholder="$t('board.form.placeholder.desc')"
          ></textarea>
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">{{ $t('board.form.iconUrl') }}</label>
          <div class="flex items-center space-x-4">
            <div v-if="form.iconUrl" class="flex-shrink-0 border border-gray-200 dark:border-gray-600 rounded-full overflow-hidden h-12 w-12">
              <img :src="form.iconUrl" alt="Icon Preview" class="h-full w-full object-contain bg-white dark:bg-gray-700">
            </div>
            <input
              type="file"
              ref="fileInput"
              @change="handleFileUpload"
              accept="image/*"
              class="block w-full text-sm text-gray-500 dark:text-gray-400
                file:mr-4 file:py-2 file:px-4
                file:rounded-full file:border-0
                file:text-sm file:font-semibold
                file:bg-indigo-50 dark:file:bg-indigo-900 file:text-indigo-700 dark:file:text-indigo-300
                hover:file:bg-indigo-100 dark:hover:file:bg-indigo-800"
            />
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

<script setup>
import { ref, reactive, watch } from 'vue'
import { useAdmin } from '@/composables/useAdmin'
import axios from 'axios'
import BaseModal from '@/components/common/BaseModal.vue'
import BaseInput from '@/components/common/BaseInput.vue'
import BaseButton from '@/components/common/BaseButton.vue'
import { useI18n } from 'vue-i18n'
import { Save, GripVertical } from 'lucide-vue-next'
import draggable from 'vuedraggable'
import logger from '@/utils/logger'
import { useToastStore } from '@/stores/toast'

const { t } = useI18n()
const toastStore = useToastStore()
const { useAdminBoards, useCreateBoard, useUpdateBoard } = useAdmin()

const boards = ref([])
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

async function handleFileUpload(event) {
  const file = event.target.files[0]
  if (!file) return

  const formData = new FormData()
  formData.append('file', file)

  try {
    const token = localStorage.getItem('accessToken')
    const { data } = await axios.post('/api/v1/files/upload', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
        'Authorization': `Bearer ${token}`
      }
    })
    
    if (data.success) {
      form.iconUrl = data.data.url
    }
  } catch (error) {
    // Error handled globally (axios interceptor or manual catch if needed, but here we rely on global if axios is configured, otherwise manual catch is fine but we want to standardize. 
    // Wait, axios calls are not covered by TanStack Query global handler. 
    // But we are standardizing error handling. For direct axios calls, we should probably still use try/catch but use toastStore instead of alert.
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

function openEditModal(board) {
  isEditMode.value = true
  form.boardName = board.boardName
  form.boardUrl = board.boardUrl
  form.description = board.description
  form.iconUrl = board.iconUrl
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
  } catch (error) {
    // Error handled globally
  } finally {
    isSubmitting.value = false
  }
}

const modifiedBoards = ref(new Set())

function handleInputChange(board) {
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

function handleSortOrderChange(board) {
  handleInputChange(board)
  boards.value.sort((a, b) => a.sortOrder - b.sortOrder)
}

async function handleSaveAll() {
  if (modifiedBoards.value.size === 0) return
  
  if (!confirm(t('common.messages.save'))) return

  isSubmitting.value = true
  try {
    const promises = Array.from(modifiedBoards.value).map(boardId => {
      const board = boards.value.find(b => b.boardId === boardId)
      return updateBoard({ 
          boardUrl: board.boardUrl, 
          data: {
            boardName: board.boardName,
            description: board.description,
            iconUrl: board.iconUrl,
            allowNsfw: board.isActive,
            sortOrder: parseInt(board.sortOrder),
            isActive: board.isActive
          }
      })
    })
    
    await Promise.all(promises)
    toastStore.addToast(t('common.messages.saveSuccess'), 'success')
    modifiedBoards.value.clear()
  } catch (error) {
    // Error handled globally
  } finally {
    isSubmitting.value = false
  }
}
</script>

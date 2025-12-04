<template>
  <div class="space-y-6">
    <div class="sm:flex sm:items-center sm:justify-between">
      <div>
        <h1 class="text-2xl font-bold text-gray-900">{{ $t('admin.boards.title') }}</h1>
        <p class="mt-2 text-sm text-gray-700">{{ $t('admin.boards.description') }}</p>
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
    <div class="bg-white shadow overflow-hidden sm:rounded-lg mb-20">
      <table class="min-w-full divide-y divide-gray-200">
        <thead class="bg-gray-50">
          <tr>
            <th scope="col" class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
              {{ $t('admin.boards.table.name') }}
            </th>
            <th scope="col" class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
              {{ $t('admin.boards.table.url') }}
            </th>
            <th scope="col" class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
              {{ $t('admin.boards.table.desc') }}
            </th>
            <th scope="col" class="px-6 py-3 text-center text-xs font-medium text-gray-500 uppercase tracking-wider">
              {{ $t('admin.boards.table.active') }}
            </th>
            <th scope="col" class="px-6 py-3 text-center text-xs font-medium text-gray-500 uppercase tracking-wider">
              {{ $t('admin.boards.table.sortOrder') }}
            </th>
          </tr>
        </thead>
        <draggable 
          v-model="boards" 
          tag="tbody" 
          item-key="boardId"
          handle=".drag-handle"
          @end="handleDragEnd"
          class="bg-white divide-y divide-gray-200"
        >
          <template #item="{ element: board }">
            <tr v-if="loading">
              <td colspan="5" class="px-6 py-4 text-center text-sm text-gray-500">
                {{ t('common.loading') }}
              </td>
            </tr>
            <tr v-else-if="boards.length === 0">
              <td colspan="5" class="px-6 py-4 text-center text-sm text-gray-500">
                {{ $t('common.noData') }}
              </td>
            </tr>
            <tr v-else :key="board.boardId">
              <td class="px-6 py-4 whitespace-nowrap text-sm font-medium text-gray-900 flex items-center">
                <GripVertical class="h-4 w-4 text-gray-400 mr-2 cursor-move drag-handle" />
                {{ board.boardName }}
              </td>
              <td class="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                {{ board.boardUrl }}
              </td>
              <td class="px-6 py-4 text-sm text-gray-500 max-w-xs">
                <input 
                  v-model="board.description" 
                  @input="handleInputChange(board)"
                  class="block w-full border-0 p-0 text-gray-500 placeholder-gray-500 focus:ring-0 sm:text-sm truncate"
                />
              </td>
              <td class="px-6 py-4 whitespace-nowrap text-center text-sm">
                <button 
                  @click="board.isActive = board.isActive === 'Y' ? 'N' : 'Y'; handleInputChange(board)"
                  class="px-2 inline-flex text-xs leading-5 font-semibold rounded-full cursor-pointer"
                  :class="board.isActive === 'Y' ? 'bg-green-100 text-green-800' : 'bg-red-100 text-red-800'"
                >
                  {{ board.isActive === 'Y' ? t('common.active') : t('common.inactive') }}
                </button>
              </td>
              <td class="px-6 py-4 whitespace-nowrap text-center text-sm text-gray-500">
                <input 
                  v-model="board.sortOrder" 
                  type="number"
                  @change="handleSortOrderChange(board)"
                  class="block w-full border-0 p-0 text-gray-500 placeholder-gray-500 focus:ring-0 sm:text-sm text-center"
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
        {{ isSubmitting ? $t('common.saving') : $t('common.saveChanges') }}
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
          <label class="block text-sm font-medium text-gray-700 mb-1">{{ $t('board.form.description') }}</label>
          <textarea
            v-model="form.description"
            rows="3"
            class="shadow-sm focus:ring-indigo-500 focus:border-indigo-500 block w-full sm:text-sm border-gray-300 rounded-md"
            :placeholder="$t('board.form.placeholder.desc')"
          ></textarea>
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">{{ $t('board.form.iconUrl') }}</label>
          <div class="flex items-center space-x-4">
            <div v-if="form.iconUrl" class="flex-shrink-0 border border-gray-200 rounded-full overflow-hidden h-12 w-12">
              <img :src="form.iconUrl" alt="Icon Preview" class="h-full w-full object-contain bg-white">
            </div>
            <input
              type="file"
              ref="fileInput"
              @change="handleFileUpload"
              accept="image/*"
              class="block w-full text-sm text-gray-500
                file:mr-4 file:py-2 file:px-4
                file:rounded-full file:border-0
                file:text-sm file:font-semibold
                file:bg-indigo-50 file:text-indigo-700
                hover:file:bg-indigo-100"
            />
          </div>
          <input type="hidden" v-model="form.iconUrl">
        </div>
        
        <div class="flex justify-end space-x-3 pt-4">
          <BaseButton @click="closeModal" variant="secondary">{{ $t('common.cancel') }}</BaseButton>
          <BaseButton @click="handleSubmit" :disabled="isSubmitting">
            {{ isSubmitting ? $t('common.saving') : $t('common.save') }}
          </BaseButton>
        </div>
      </div>
    </BaseModal>
  </div>
</template>

<script setup>
import { ref, onMounted, reactive } from 'vue'
import { adminApi } from '@/api/admin'
import axios from 'axios'
import BaseModal from '@/components/common/BaseModal.vue'
import BaseInput from '@/components/common/BaseInput.vue'
import BaseButton from '@/components/common/BaseButton.vue'
import { useI18n } from 'vue-i18n'
import { Save, GripVertical } from 'lucide-vue-next'
import draggable from 'vuedraggable'

const { t } = useI18n()

const boards = ref([])
const loading = ref(false)
const isModalOpen = ref(false)
const isEditMode = ref(false)
const isSubmitting = ref(false)
const fileInput = ref(null)

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
    // Assuming you have a file upload API endpoint
    // If you don't have a dedicated one in adminApi, you might need to add it or use axios directly
    // Here using a hypothetical endpoint based on FileController
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
    console.error('Failed to upload file:', error)
    alert(t('common.error'))
  }
}

async function fetchBoards() {
  loading.value = true
  try {
    const { data } = await adminApi.getBoards()
    if (data.success) {
      boards.value = data.data
    }
  } catch (error) {
    console.error('Failed to fetch boards:', error)
  } finally {
    loading.value = false
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
    alert(t('board.writePost.validation'))
    return
  }

  isSubmitting.value = true
  try {
    const payload = { ...form }
    let response
    
    if (isEditMode.value) {
      response = await adminApi.updateBoard(form.boardUrl, payload)
    } else {
      response = await adminApi.createBoard(payload)
    }

    if (response.data.success) {
      alert(isEditMode.value ? t('admin.boards.messages.updated') : t('admin.boards.messages.created'))
      closeModal()
      fetchBoards()
    }
  } catch (error) {
    console.error('Failed to save board:', error)
    alert(isEditMode.value ? t('admin.boards.messages.updateFailed') : t('admin.boards.messages.createFailed'))
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
      return adminApi.updateBoard(board.boardUrl, {
        boardName: board.boardName,
        description: board.description,
        iconUrl: board.iconUrl,
        allowNsfw: board.isActive === 'Y',
        sortOrder: parseInt(board.sortOrder),
        isActive: board.isActive === 'Y' // Also send isActive if needed, though updateBoard might not use it directly for activation/deactivation
      })
    })
    
    await Promise.all(promises)
    alert(t('common.messages.saveSuccess'))
    modifiedBoards.value.clear()
    fetchBoards()
  } catch (error) {
    console.error('Failed to save boards:', error)
    alert(t('common.messages.saveFailed'))
  } finally {
    isSubmitting.value = false
  }
}



onMounted(() => {
  fetchBoards()
})
</script>

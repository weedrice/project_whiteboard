<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ChevronDown, List } from 'lucide-vue-next'
import axios from '@/api'
import { useAuthStore } from '@/stores/auth'
import logger from '@/utils/logger'
import BaseButton from '@/components/common/BaseButton.vue'

const props = defineProps<{
  type: 'subscription' | 'all'
  isOpen: boolean
}>()

const emit = defineEmits<{
  (e: 'toggle'): void
}>()

const router = useRouter()
const authStore = useAuthStore()
const dropdownRef = ref<HTMLElement | null>(null)
const items = ref<any[]>([])
const loading = ref(false)

const toggleDropdown = () => {
  emit('toggle')
}

// Watch isOpen to fetch items when opened
watch(() => props.isOpen, (newVal) => {
  if (newVal && items.value.length === 0) {
    fetchItems()
  }
})

const fetchItems = async () => {
  loading.value = true
  try {
    let response
    if (props.type === 'subscription') {
      response = await axios.get('/users/me/subscriptions', { params: { size: 10 } })
    } else {
      response = await axios.get('/boards/top')
    }

    if (response.data.success) {
      if (props.type === 'subscription') {
        items.value = response.data.data.content
      } else {
        items.value = response.data.data
      }
    }
  } catch (error) {
    logger.error('Failed to fetch boards:', error)
  } finally {
    loading.value = false
  }
}

const handleMoreClick = () => {
  emit('toggle') // Close dropdown
  router.push('/boards')
}

// Re-fetch subscriptions if user logs in/out and dropdown is open
watch(() => authStore.isAuthenticated, (newVal) => {
  if (newVal && props.type === 'subscription' && props.isOpen) {
    fetchItems()
  }
})
</script>

<template>
  <div class="relative" ref="dropdownRef">
    <BaseButton @click.stop="toggleDropdown" variant="ghost"
      class="flex items-center space-x-1 text-gray-500 dark:text-gray-400 hover:text-gray-900 dark:hover:text-gray-200 px-3 py-2 rounded-md text-sm font-medium focus:outline-none">
      <span v-if="type === 'subscription'">{{ $t('board.list.subscribed') }}</span>
      <span v-else>{{ $t('board.list.all') }}</span>
      <ChevronDown class="h-4 w-4" />
    </BaseButton>

    <div v-if="isOpen"
      class="origin-top-left absolute left-0 mt-2 w-64 rounded-md shadow-lg py-1 bg-white dark:bg-gray-800 ring-1 ring-black ring-opacity-5 dark:ring-gray-700 focus:outline-none z-50">
      <div v-if="loading" class="px-4 py-3 text-center">
        <div class="animate-spin rounded-full h-5 w-5 border-b-2 border-indigo-600 mx-auto"></div>
      </div>

      <div v-else-if="items.length > 0">
        <div class="max-h-96 overflow-y-auto">
          <router-link v-for="board in items" :key="board.boardUrl" :to="`/board/${board.boardUrl}`"
            class="group flex items-center px-4 py-2 text-sm text-gray-700 dark:text-gray-200 hover:bg-gray-50 dark:hover:bg-gray-700 hover:text-gray-900 dark:hover:text-white"
            @click="emit('toggle')">
            <span class="truncate">{{ board.boardName }}</span>
          </router-link>
        </div>

        <div v-if="type === 'all'" class="border-t border-gray-100 dark:border-gray-700 pt-1">
          <BaseButton @click="handleMoreClick" variant="ghost" full-width
            class="w-full text-left group flex items-center px-4 py-2 text-sm text-indigo-600 dark:text-indigo-400 hover:bg-indigo-50 dark:hover:bg-indigo-900/50 font-medium justify-start">
            <List class="mr-2 h-4 w-4" />
            {{ $t('common.loadMore') }}
          </BaseButton>
        </div>
      </div>

      <div v-else class="px-4 py-3 text-sm text-gray-500 dark:text-gray-400 text-center">
        <span v-if="type === 'subscription'">{{ $t('board.list.noSubscribed') }}</span>
        <span v-else>{{ $t('board.list.noBoards') }}</span>
      </div>
    </div>
  </div>
</template>

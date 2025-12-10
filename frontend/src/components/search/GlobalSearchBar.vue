<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useBoard } from '@/composables/useBoard'
import { Search } from 'lucide-vue-next'

const router = useRouter()
const { useBoards } = useBoard()
const { data: boardsData } = useBoards()

const searchQuery = ref('')
const showDropdown = ref(false)
const searchContainer = ref<HTMLElement | null>(null)

const boards = computed(() => boardsData.value || [])
const filteredBoards = computed(() => {
    if (!searchQuery.value.trim()) return []
    const query = searchQuery.value.toLowerCase()
    return boards.value.filter((board: any) => 
        board.boardName.toLowerCase().includes(query)
    )
})

// Handle search submission (Full Search)
const handleSearch = () => {
  if (searchQuery.value.trim()) {
    showDropdown.value = false
    router.push({ name: 'search', query: { q: searchQuery.value } })
  }
}

// Handle board selection (Autocomplete)
const selectBoard = (boardUrl: string) => {
  showDropdown.value = false
  searchQuery.value = '' // Optional: clear search after navigation
  router.push(`/board/${boardUrl}`)
}

// Watch for input changes
watch(searchQuery, () => {
  showDropdown.value = !!searchQuery.value.trim()
})

// Click outside to close dropdown
const handleClickOutside = (event: Event) => {
  if (searchContainer.value && !searchContainer.value.contains(event.target as Node)) {
    showDropdown.value = false
  }
}

onMounted(() => {
  document.addEventListener('click', handleClickOutside)
})

onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside)
})
</script>

<template>
  <div ref="searchContainer" class="relative w-full max-w-md">
    <div class="relative">
      <input 
        type="text" 
        v-model="searchQuery"
        @keyup.enter="handleSearch"
        @focus="showDropdown = !!searchQuery.trim()"
        :placeholder="$t('common.searchPlaceholder')" 
        class="w-64 pl-10 pr-4 py-2 rounded-full border border-gray-300 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent text-sm dark:bg-gray-700 dark:border-gray-600 dark:text-white dark:placeholder-gray-400"
      >
      <div class="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
        <Search class="h-4 w-4 text-gray-400 dark:text-gray-500" />
      </div>
    </div>

    <!-- Autocomplete Dropdown -->
    <div v-if="showDropdown" class="absolute z-50 mt-1 w-full bg-white dark:bg-gray-800 rounded-md shadow-lg border border-gray-200 dark:border-gray-700 max-h-96 overflow-y-auto">
      <!-- Board Results -->
      <div v-if="filteredBoards.length > 0">
        <div class="px-3 py-2 text-xs font-semibold text-gray-500 dark:text-gray-400 bg-gray-50 dark:bg-gray-700">
          {{ $t('search.boards') }}
        </div>
        <ul>
          <li 
            v-for="board in filteredBoards" 
            :key="board.boardUrl"
            @click="selectBoard(board.boardUrl)"
            class="px-4 py-2 hover:bg-gray-100 dark:hover:bg-gray-700 cursor-pointer flex items-center space-x-3"
          >
            <!-- Board Icon/Thumbnail -->
            <div class="flex-shrink-0 h-8 w-8 rounded bg-indigo-100 flex items-center justify-center text-indigo-600 font-bold overflow-hidden border border-gray-200">
               <img v-if="board.iconUrl" :src="board.iconUrl" class="h-full w-full object-contain bg-white" alt="" />
               <span v-else class="text-xs">{{ board.boardName.substring(0, 1) }}</span>
            </div>
            <span class="text-sm text-gray-900 dark:text-white font-medium">{{ board.boardName }}</span>
          </li>
        </ul>
      </div>

      <!-- Full Search Option -->
      <div 
        @click="handleSearch"
        class="border-t border-gray-200 dark:border-gray-700 px-4 py-3 hover:bg-gray-100 dark:hover:bg-gray-700 cursor-pointer flex items-center text-indigo-600 dark:text-indigo-400"
      >
        <Search class="h-4 w-4 mr-2" />
        <span class="text-sm font-medium">{{ $t('search.doSearch', { query: searchQuery }) }}</span>
      </div>
    </div>
  </div>
</template>

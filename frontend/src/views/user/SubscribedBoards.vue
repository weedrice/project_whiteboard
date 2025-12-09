<template>
  <div class="bg-white shadow rounded-lg overflow-hidden">
    <div class="px-4 py-5 sm:px-6">
        <h3 class="text-lg font-medium leading-6 text-gray-900">{{ $t('user.subscriptions.title') }}</h3>
    </div>

    <div v-if="loading" class="text-center py-10">
      <div class="animate-spin rounded-full h-10 w-10 border-b-2 border-indigo-600 mx-auto"></div>
    </div>

    <div v-else-if="boards.length === 0" class="text-center py-10 text-gray-500">
        {{ $t('user.subscriptions.empty') }}
    </div>

    <draggable 
        v-else 
        v-model="boards" 
        item-key="boardId" 
        class="divide-y divide-gray-200" 
        tag="ul"
        handle=".handle"
        @end="handleDragEnd"
    >
      <template #item="{ element: board }">
        <li class="px-4 py-4 sm:px-6 hover:bg-gray-50 flex justify-between items-center bg-white">
            <div class="flex items-center flex-1 cursor-pointer">
                <div class="handle mr-4 cursor-move text-gray-400 hover:text-gray-600">
                    <Menu class="h-5 w-5" />
                </div>
                <div class="flex-1" @click="$router.push(`/board/${board.boardUrl}`)">
                    <div class="text-sm font-medium text-indigo-600 truncate">{{ board.boardName }}</div>
                    <p class="mt-1 text-sm text-gray-500">{{ board.description }}</p>
                </div>
            </div>
            <button 
                @click="handleUnsubscribe(board)"
                class="ml-4 px-3 py-1 border border-transparent text-sm font-medium rounded-md text-red-700 bg-red-100 hover:bg-red-200 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-red-500"
            >
                {{ $t('user.subscriptions.unsubscribe') }}
            </button>
        </li>
      </template>
    </draggable>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { userApi } from '@/api/user'
import { boardApi } from '@/api/board'
import { useI18n } from 'vue-i18n'
import draggable from 'vuedraggable'
import { Menu } from 'lucide-vue-next'

const { t } = useI18n()
const boards = ref([])
const loading = ref(false)

async function fetchSubscriptions() {
    loading.value = true
    try {
        const { data } = await userApi.getMySubscriptions()
        if (data.success) {
            boards.value = data.data.content
        }
    } catch (error) {
        logger.error(error)
    } finally {
        loading.value = false
    }
}

async function handleUnsubscribe(board) {
    if (!confirm(t('user.subscriptions.unsubscribeConfirm'))) return
    try {
        const { data } = await boardApi.unsubscribeBoard(board.boardUrl)
        if (data.success) {
            alert(t('user.subscriptions.unsubscribeSuccess'))
            fetchSubscriptions() // Refresh list
        }
    } catch (error) {
        logger.error(error)
        alert(t('user.subscriptions.unsubscribeFailed'))
    }
}

async function handleDragEnd() {
    const boardUrls = boards.value.map(b => b.boardUrl)
    try {
        await boardApi.updateSubscriptionOrder(boardUrls)
    } catch (error) {
        logger.error('Failed to update order:', error)
        // Revert order if failed (optional, but good UX)
        fetchSubscriptions()
    }
}

onMounted(() => {
    fetchSubscriptions()
})
</script>

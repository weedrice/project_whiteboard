<template>
    <div class="max-w-4xl mx-auto py-4 sm:py-6 md:py-8 px-4 sm:px-6 lg:px-8">
        <div class="bg-white dark:bg-gray-800 shadow overflow-hidden sm:rounded-lg transition-colors duration-200">
            <div class="px-4 py-4 sm:py-5 sm:px-6 border-b border-gray-200 dark:border-gray-700 flex items-center">
                <Users class="h-5 w-5 mr-2 text-gray-500 dark:text-gray-400 flex-shrink-0" />
                <h3 class="text-lg leading-6 font-medium text-gray-900 dark:text-white">{{ $t('user.subscriptions.title') }}</h3>
            </div>

            <div v-if="loading" class="divide-y divide-gray-200 dark:divide-gray-700">
                <div v-for="i in 5" :key="i" class="px-4 py-4 sm:px-6 flex justify-between items-center">
                    <div class="flex items-center flex-1">
                        <BaseSkeleton width="20px" height="20px" className="mr-4" />
                        <div class="flex-1">
                            <BaseSkeleton width="120px" height="20px" className="mb-1" />
                            <BaseSkeleton width="200px" height="16px" />
                        </div>
                    </div>
                    <BaseSkeleton width="80px" height="32px" className="ml-4" />
                </div>
            </div>

            <EmptyState
                v-else-if="!hasSubscriptions"
                :title="$t('user.subscriptions.empty')"
                :icon="Users"
            />

            <draggable v-else-if="accessibleBoards.length > 0" v-model="accessibleBoards" item-key="boardId" class="divide-y divide-gray-200 dark:divide-gray-700"
                tag="ul" :handle="isMobile ? undefined : '.handle'" :disabled="isMobile" @end="handleDragEnd">
                <template #item="{ element: board }">
                    <li
                        class="px-3 py-3 sm:px-6 sm:py-4 hover:bg-gray-50 dark:hover:bg-gray-700 flex flex-row items-center justify-between gap-2 sm:gap-3 bg-white dark:bg-gray-800 transition-colors duration-200">
                        <router-link
                            v-if="isAccessibleSubscription(board)"
                            :to="`/board/${board.boardUrl}`"
                            class="flex items-center flex-1 min-w-0"
                            :aria-label="board.boardName || $t('user.subscriptions.unavailableBoard')">
                            <div
                                v-if="!isMobile && isAccessibleSubscription(board)"
                                class="handle mr-3 sm:mr-4 p-2 -m-2 cursor-move text-gray-400 hover:text-gray-600 dark:text-gray-500 dark:hover:text-gray-300 touch-manipulation min-w-[44px] min-h-[44px] flex items-center justify-center"
                                @click.prevent.stop>
                                <Menu class="h-5 w-5" />
                            </div>
                            <div class="flex-1 min-w-0 py-1 sm:py-0">
                                <div class="text-sm font-medium text-indigo-600 dark:text-indigo-400 truncate">{{
                                    board.boardName || $t('user.subscriptions.unavailableBoard') }}</div>
                                <p class="mt-0.5 sm:mt-1 text-xs sm:text-sm text-gray-500 dark:text-gray-400 line-clamp-1 sm:line-clamp-2">{{
                                    board.description || $t('user.subscriptions.unavailableBoardDescription') }}</p>
                            </div>
                        </router-link>
                        <div v-else class="flex items-center flex-1 min-w-0 cursor-default">
                            <div class="flex-1 min-w-0 py-1 sm:py-0">
                                <div class="text-sm font-medium text-indigo-600 dark:text-indigo-400 truncate">{{
                                    board.boardName || $t('user.subscriptions.unavailableBoard') }}</div>
                                <p class="mt-0.5 sm:mt-1 text-xs sm:text-sm text-gray-500 dark:text-gray-400 line-clamp-1 sm:line-clamp-2">{{
                                    board.description || $t('user.subscriptions.unavailableBoardDescription') }}</p>
                            </div>
                        </div>
                        <BaseButton
                            @click.stop="handleUnsubscribe(board)"
                            variant="danger"
                            size="sm"
                            class="flex-shrink-0 px-2 py-1.5 sm:px-4 sm:py-2 text-[11px] sm:text-sm min-h-0 h-7 sm:min-h-[40px] rounded-md sm:rounded-lg touch-manipulation">
                            {{ $t('user.subscriptions.unsubscribe') }}
                        </BaseButton>
                    </li>
                </template>
            </draggable>

            <ul v-if="unavailableBoards.length > 0" class="divide-y divide-gray-200 dark:divide-gray-700">
                <li
                    v-for="board in unavailableBoards"
                    :key="board.boardId"
                    class="px-3 py-3 sm:px-6 sm:py-4 hover:bg-gray-50 dark:hover:bg-gray-700 flex flex-row items-center justify-between gap-2 sm:gap-3 bg-white dark:bg-gray-800 transition-colors duration-200">
                    <div class="flex items-center flex-1 min-w-0 cursor-default">
                        <div class="flex-1 min-w-0 py-1 sm:py-0">
                            <div class="text-sm font-medium text-indigo-600 dark:text-indigo-400 truncate">{{
                                board.boardName || $t('user.subscriptions.unavailableBoard') }}</div>
                            <p class="mt-0.5 sm:mt-1 text-xs sm:text-sm text-gray-500 dark:text-gray-400 line-clamp-1 sm:line-clamp-2">{{
                                board.description || $t('user.subscriptions.unavailableBoardDescription') }}</p>
                        </div>
                    </div>
                    <BaseButton @click.stop="handleUnsubscribe(board)" variant="danger" size="sm"
                        class="flex-shrink-0 px-2 py-1.5 sm:px-4 sm:py-2 text-[11px] sm:text-sm min-h-0 h-7 sm:min-h-[40px] rounded-md sm:rounded-lg touch-manipulation">
                        {{ $t('user.subscriptions.unsubscribe') }}
                    </BaseButton>
                </li>
            </ul>
        </div>
    </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted, onUnmounted } from 'vue'
import { useQueryClient } from '@tanstack/vue-query'
import { userApi } from '@/api/user'
import { boardApi } from '@/api/board'
import { useI18n } from 'vue-i18n'
import { useToastStore } from '@/stores/toast'
import draggable from 'vuedraggable'
import { Menu, Users } from 'lucide-vue-next'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseSkeleton from '@/components/common/ui/BaseSkeleton.vue'
import EmptyState from '@/components/common/ui/EmptyState.vue'
import { useConfirm } from '@/composables/useConfirm'
import { useErrorHandler } from '@/composables/useErrorHandler'
import type { SubscriptionBoardListItem } from '@/types'

const { t } = useI18n()
const toastStore = useToastStore()
const queryClient = useQueryClient()
const { confirm } = useConfirm()
const { handleSilentError, handleError } = useErrorHandler()
const accessibleBoards = ref<SubscriptionBoardListItem[]>([])
const unavailableBoards = ref<SubscriptionBoardListItem[]>([])
const loading = ref(false)
const isMobile = ref(typeof window !== 'undefined' && window.innerWidth < 640)
const subscriptionsPageSize = 100
let subscriptionsRequestId = 0
const hasSubscriptions = computed(() =>
    accessibleBoards.value.length > 0 || unavailableBoards.value.length > 0
)

function updateIsMobile() {
    isMobile.value = window.innerWidth < 640
}

function isAccessibleSubscription(board: SubscriptionBoardListItem) {
    return board.accessState === 'ACCESSIBLE'
}

function canReorderSubscription(board: SubscriptionBoardListItem) {
    return isAccessibleSubscription(board) && board.isActive !== false
}

function invalidateSubscriptionCaches() {
    queryClient.invalidateQueries({ queryKey: ['boards'] })
    queryClient.invalidateQueries({ queryKey: ['boards', 'subscriptions'] })
}

async function fetchAllSubscriptions() {
    const boards: SubscriptionBoardListItem[] = []
    let page = 0
    let totalPages = 1

    while (page < totalPages) {
        const { data } = await userApi.getMySubscriptions({
            page,
            size: subscriptionsPageSize,
            includeUnavailable: true
        })

        if (!data.success) {
            return null
        }

        boards.push(...data.data.content)
        totalPages = data.data.totalPages
        page += 1
    }

    return boards
}

async function fetchSubscriptions() {
    const requestId = ++subscriptionsRequestId
    loading.value = true
    try {
        const boards = await fetchAllSubscriptions()
        if (requestId !== subscriptionsRequestId) {
            return
        }

        if (boards !== null) {
            accessibleBoards.value = boards.filter(canReorderSubscription)
            unavailableBoards.value = boards.filter(board => !canReorderSubscription(board))
        }
    } catch (error) {
        if (requestId !== subscriptionsRequestId) {
            return
        }

        handleSilentError(error, 'Failed to load subscriptions')
    } finally {
        if (requestId === subscriptionsRequestId) {
            loading.value = false
        }
    }
}

async function handleUnsubscribe(board: SubscriptionBoardListItem) {
    const isConfirmed = await confirm(t('user.subscriptions.unsubscribeConfirm'))
    if (!isConfirmed) return
    try {
        const { data } = await boardApi.unsubscribeBoard(board.boardUrl)
        if (data.success) {
            toastStore.addToast(t('user.subscriptions.unsubscribeSuccess'), 'success')
            invalidateSubscriptionCaches()
            await fetchSubscriptions()
        }
    } catch (error) {
        handleError(error, t('user.subscriptions.unsubscribeFailed'))
    }
}

async function handleDragEnd() {
    const boardUrls = accessibleBoards.value.map(board => board.boardUrl)
    try {
        await boardApi.updateSubscriptionOrder(boardUrls)
        invalidateSubscriptionCaches()
    } catch (error) {
        handleSilentError(error, 'Failed to update subscription order')
        // Revert order if failed (optional, but good UX)
        await fetchSubscriptions()
    }
}

onMounted(() => {
    fetchSubscriptions()
    window.addEventListener('resize', updateIsMobile)
})

onUnmounted(() => {
    subscriptionsRequestId += 1
    window.removeEventListener('resize', updateIsMobile)
})
</script>

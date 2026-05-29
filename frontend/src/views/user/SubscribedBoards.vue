<template>
    <div class="max-w-4xl mx-auto py-4 sm:py-6 md:py-8 px-4 sm:px-6 lg:px-8">
        <div class="nv-surface shadow overflow-hidden sm:rounded-lg transition-colors duration-200">
            <div class="px-4 py-4 sm:py-5 sm:px-6 border-b nv-border flex items-center">
                <Users class="h-5 w-5 mr-2 nv-text-subtle flex-shrink-0" />
                <h3 class="text-lg leading-6 font-medium nv-title">{{ $t('user.subscriptions.title') }}</h3>
            </div>

            <div v-if="loading" class="divide-y divide-[var(--nv-border)]">
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

            <draggable v-else-if="accessibleBoards.length > 0" v-model="accessibleBoards" item-key="boardId" class="divide-y divide-[var(--nv-border)]"
                tag="ul" :handle="isMobile ? undefined : '.handle'" :disabled="isMobile" @end="handleDragEnd">
                <template #item="{ element: board }">
                    <li
                        class="px-3 py-3 sm:px-6 sm:py-4 nv-hover-surface flex flex-row items-center justify-between gap-2 sm:gap-3 transition-colors duration-200">
                        <router-link
                            v-if="isAccessibleSubscription(board)"
                            :to="`/board/${board.boardUrl}`"
                            class="flex items-center flex-1 min-w-0"
                            :aria-label="board.boardName || $t('user.subscriptions.unavailableBoard')">
                            <div
                                v-if="!isMobile && isAccessibleSubscription(board)"
                                class="handle mr-3 sm:mr-4 p-2 -m-2 cursor-move nv-text-subtle touch-manipulation min-w-[44px] min-h-[44px] flex items-center justify-center"
                                @click.prevent.stop>
                                <Menu class="h-5 w-5" />
                            </div>
                            <div class="flex-1 min-w-0 py-1 sm:py-0">
                                <div class="text-sm font-medium text-indigo-600 dark:text-indigo-400 truncate">{{
                                    board.boardName || $t('user.subscriptions.unavailableBoard') }}</div>
                                <p class="mt-0.5 sm:mt-1 text-xs sm:text-sm nv-text-subtle line-clamp-1 sm:line-clamp-2">{{
                                    board.description || $t('user.subscriptions.unavailableBoardDescription') }}</p>
                            </div>
                        </router-link>
                        <div v-else class="flex items-center flex-1 min-w-0 cursor-default">
                            <div class="flex-1 min-w-0 py-1 sm:py-0">
                                <div class="text-sm font-medium text-indigo-600 dark:text-indigo-400 truncate">{{
                                    board.boardName || $t('user.subscriptions.unavailableBoard') }}</div>
                                <p class="mt-0.5 sm:mt-1 text-xs sm:text-sm nv-text-subtle line-clamp-1 sm:line-clamp-2">{{
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

            <ul v-if="unavailableBoards.length > 0" class="divide-y divide-[var(--nv-border)]">
                <li
                    v-for="board in unavailableBoards"
                    :key="board.boardId"
                    class="px-3 py-3 sm:px-6 sm:py-4 nv-hover-surface flex flex-row items-center justify-between gap-2 sm:gap-3 transition-colors duration-200">
                    <div class="flex items-center flex-1 min-w-0 cursor-default">
                        <div class="flex-1 min-w-0 py-1 sm:py-0">
                            <div class="text-sm font-medium text-indigo-600 dark:text-indigo-400 truncate">{{
                                board.boardName || $t('user.subscriptions.unavailableBoard') }}</div>
                            <p class="mt-0.5 sm:mt-1 text-xs sm:text-sm nv-text-subtle line-clamp-1 sm:line-clamp-2">{{
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
import draggable from 'vuedraggable'
import { Menu, Users } from 'lucide-vue-next'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseSkeleton from '@/components/common/ui/BaseSkeleton.vue'
import EmptyState from '@/components/common/ui/EmptyState.vue'
import { useSubscribedBoardsManager } from '@/composables/useSubscribedBoardsManager'

const {
    accessibleBoards,
    unavailableBoards,
    loading,
    isMobile,
    hasSubscriptions,
    handleDragEnd,
    handleUnsubscribe,
    isAccessibleSubscription,
} = useSubscribedBoardsManager()
</script>

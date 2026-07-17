<template>
    <div class="mx-auto max-w-4xl">
        <div class="nv-surface nv-elevated-surface shadow overflow-hidden sm:rounded-lg transition-colors duration-200">
            <div class="px-4 py-4 sm:py-5 sm:px-6 border-b nv-border flex items-center">
                <Users class="h-5 w-5 mr-2 nv-text-subtle flex-shrink-0" />
                <h1 class="text-lg leading-6 font-medium nv-title">{{ $t('user.subscriptions.title') }}</h1>
            </div>

            <div v-if="loading" class="divide-y divide-[var(--nv-line)]">
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

            <ErrorState
                v-else-if="loadError"
                :message="$t('common.messages.loadFailed')"
                show-retry
                auto-focus
                @retry="fetchSubscriptions"
            />

            <EmptyState
                v-else-if="!hasSubscriptions"
                title-tag="h2"
                :title="$t('user.subscriptions.empty')"
                :icon="Users"
            />

            <draggable v-else-if="accessibleBoards.length > 0" v-model="accessibleBoards" item-key="boardId" class="divide-y divide-[var(--nv-line)]"
                tag="ul" :handle="isMobile ? undefined : '.handle'" :disabled="isMobile" @end="handleDragEnd">
                <template #item="{ element: board, index }">
                    <li
                        class="px-3 py-3 sm:px-6 sm:py-4 nv-hover-surface flex flex-row items-center justify-between gap-2 sm:gap-3 transition-colors duration-200">
                        <div
                            v-if="isAccessibleSubscription(board) && accessibleBoards.length > 1"
                            class="flex shrink-0 items-center gap-1"
                            role="group"
                            :aria-label="$t('user.subscriptions.reorder', { name: board.boardName })"
                        >
                            <div
                                class="handle hidden min-h-9 min-w-9 cursor-move items-center justify-center nv-text-subtle sm:flex"
                                aria-hidden="true"
                            >
                                <GripVertical class="h-4 w-4" />
                            </div>
                            <button
                                type="button"
                                class="subscription-order-button nv-focus-ring inline-flex min-h-11 min-w-11 cursor-pointer items-center justify-center rounded-full nv-text-subtle nv-hover-surface disabled:cursor-not-allowed disabled:opacity-40 sm:min-h-9 sm:min-w-9"
                                :aria-label="$t('user.subscriptions.moveUp', { name: board.boardName })"
                                :disabled="index === 0 || isReordering"
                                @click.stop="moveSubscription(board.boardUrl, 'up')"
                            >
                                <ChevronUp class="h-4 w-4" aria-hidden="true" />
                            </button>
                            <button
                                type="button"
                                class="subscription-order-button nv-focus-ring inline-flex min-h-11 min-w-11 cursor-pointer items-center justify-center rounded-full nv-text-subtle nv-hover-surface disabled:cursor-not-allowed disabled:opacity-40 sm:min-h-9 sm:min-w-9"
                                :aria-label="$t('user.subscriptions.moveDown', { name: board.boardName })"
                                :disabled="index === accessibleBoards.length - 1 || isReordering"
                                @click.stop="moveSubscription(board.boardUrl, 'down')"
                            >
                                <ChevronDown class="h-4 w-4" aria-hidden="true" />
                            </button>
                        </div>
                        <router-link
                            v-if="isAccessibleSubscription(board)"
                            :to="`/board/${encodePathSegment(board.boardUrl)}`"
                            class="flex items-center flex-1 min-w-0"
                            :aria-label="board.boardName || $t('user.subscriptions.unavailableBoard')">
                            <div class="flex-1 min-w-0 py-1 sm:py-0">
                                <div class="flex items-center gap-2">
                                    <div class="text-sm font-medium nv-accent-text truncate">{{
                                        board.boardName || $t('user.subscriptions.unavailableBoard') }}</div>
                                    <span
                                        v-if="board.hasNewPosts"
                                        class="inline-flex h-5 min-w-5 shrink-0 items-center justify-center rounded-full bg-[var(--nv-accent)] px-1.5 text-xs font-semibold text-white"
                                    >
                                        {{ formatNewPostCount(board.newPostCount) }}
                                    </span>
                                </div>
                                <p class="mt-0.5 sm:mt-1 text-xs sm:text-sm nv-text-subtle line-clamp-1 sm:line-clamp-2">{{
                                    board.description || $t('user.subscriptions.unavailableBoardDescription') }}</p>
                            </div>
                        </router-link>
                        <div v-else class="flex items-center flex-1 min-w-0 cursor-default">
                            <div class="flex-1 min-w-0 py-1 sm:py-0">
                                <div class="text-sm font-medium nv-accent-text truncate">{{
                                    board.boardName || $t('user.subscriptions.unavailableBoard') }}</div>
                                <p class="mt-0.5 sm:mt-1 text-xs sm:text-sm nv-text-subtle line-clamp-1 sm:line-clamp-2">{{
                                    board.description || $t('user.subscriptions.unavailableBoardDescription') }}</p>
                            </div>
                        </div>
                        <BaseButton
                            @click.stop="handleUnsubscribe(board)"
                            variant="danger"
                            size="sm"
                            class="min-h-11 flex-shrink-0 rounded-md px-3 py-1.5 text-xs touch-manipulation sm:min-h-10 sm:rounded-lg sm:px-4 sm:py-2 sm:text-sm">
                            {{ $t('user.subscriptions.unsubscribe') }}
                        </BaseButton>
                    </li>
                </template>
            </draggable>

            <ul v-if="unavailableBoards.length > 0" class="divide-y divide-[var(--nv-line)]">
                <li
                    v-for="board in unavailableBoards"
                    :key="board.boardId"
                    class="px-3 py-3 sm:px-6 sm:py-4 nv-hover-surface flex flex-row items-center justify-between gap-2 sm:gap-3 transition-colors duration-200">
                    <div class="flex items-center flex-1 min-w-0 cursor-default">
                        <div class="flex-1 min-w-0 py-1 sm:py-0">
                            <div class="text-sm font-medium nv-accent-text truncate">{{
                                board.boardName || $t('user.subscriptions.unavailableBoard') }}</div>
                            <p class="mt-0.5 sm:mt-1 text-xs sm:text-sm nv-text-subtle line-clamp-1 sm:line-clamp-2">{{
                                board.description || $t('user.subscriptions.unavailableBoardDescription') }}</p>
                        </div>
                    </div>
                    <BaseButton @click.stop="handleUnsubscribe(board)" variant="danger" size="sm"
                        class="min-h-11 flex-shrink-0 rounded-md px-3 py-1.5 text-xs touch-manipulation sm:min-h-10 sm:rounded-lg sm:px-4 sm:py-2 sm:text-sm">
                        {{ $t('user.subscriptions.unsubscribe') }}
                    </BaseButton>
                </li>
            </ul>
        </div>
    </div>
</template>

<script setup lang="ts">
import draggable from 'vuedraggable'
import { ChevronDown, ChevronUp, GripVertical, Users } from 'lucide-vue-next'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseSkeleton from '@/components/common/ui/BaseSkeleton.vue'
import EmptyState from '@/components/common/ui/EmptyState.vue'
import ErrorState from '@/components/common/ui/ErrorState.vue'
import { useSubscribedBoardsManager } from '@/features/user/subscriptions/useSubscribedBoardsManager'
import { encodePathSegment } from '@/utils/urlPath'

const {
    accessibleBoards,
    unavailableBoards,
    loading,
    loadError,
    isReordering,
    isMobile,
    hasSubscriptions,
    fetchSubscriptions,
    handleDragEnd,
    moveSubscription,
    handleUnsubscribe,
    isAccessibleSubscription,
} = useSubscribedBoardsManager()

function formatNewPostCount(count: number) {
    if (count > 99) return '99+'
    return String(count)
}
</script>

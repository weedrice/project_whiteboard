<script setup lang="ts">
import { computed, watch } from 'vue'
import { useApiQuery } from '@/composables/useApiQuery'
import { boardApi } from '@/api/board'
import { useRecentBoards } from '@/features/board/recent/useRecentBoards'
import { X } from 'lucide-vue-next'
import { getOptimizedBoardIconUrl, handleImageError } from '@/utils/image'
import { encodePathSegment } from '@/utils/urlPath'

const { recentBoards, removeRecentBoard, clearRecentBoards } = useRecentBoards()

const hasBoards = computed(() => recentBoards.value.length > 0)
const recentBoardUrls = computed(() => recentBoards.value.map(board => board.boardUrl))
const { data: recentUpdates, isSuccess: recentUpdatesLoaded } = useApiQuery({
    queryKey: computed(() => ['boards', 'recent-updates', recentBoardUrls.value]),
    request: ({ signal }) => boardApi.getRecentBoardUpdates(recentBoardUrls.value, { signal }),
    enabled: hasBoards,
    staleTime: 60_000,
    meta: { authScoped: true },
})
const accessibleBoardUrls = computed(() => new Set((recentUpdates.value ?? []).map(update => update.boardUrl)))
const verifiedRecentBoards = computed(() => recentUpdatesLoaded.value
    ? recentBoards.value.filter(board => accessibleBoardUrls.value.has(board.boardUrl))
    : [])
const hasVerifiedBoards = computed(() => verifiedRecentBoards.value.length > 0)

watch(recentUpdates, (updates) => {
    if (!updates) return
    const accessible = new Set(updates.map(update => update.boardUrl))
    recentBoards.value
        .filter(board => !accessible.has(board.boardUrl))
        .forEach(board => removeRecentBoard(board.boardUrl))
}, { immediate: true })
const latestPostAtByBoardUrl = computed(() => new Map(
    (recentUpdates.value ?? []).map(update => [update.boardUrl, update.latestPostAt])
))

function hasNewPosts(boardUrl: string, visitedAt: string) {
    const latestPostAt = latestPostAtByBoardUrl.value.get(boardUrl)
    return !!latestPostAt && new Date(latestPostAt).getTime() > new Date(visitedAt).getTime()
}
</script>

<template>
    <div v-if="hasVerifiedBoards" class="recent-boards-bar">
        <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
            <div class="recent-boards-inner">
                <span class="recent-boards-label">{{ $t('layout.recentBoards.title') }}</span>
                <div class="recent-boards-list">
                    <div v-for="board in verifiedRecentBoards" :key="board.boardUrl" class="recent-board-chip">
                        <router-link :to="`/board/${encodePathSegment(board.boardUrl)}`" class="recent-board-link">
                        <img v-if="board.iconUrl" :src="getOptimizedBoardIconUrl(board.iconUrl, 24)" loading="lazy" decoding="async"
                            class="recent-board-icon" alt="" @error="handleImageError($event)" />
                        <span v-else class="recent-board-icon-fallback">{{ board.boardName[0] }}</span>
                        <span class="recent-board-name">{{ board.boardName }}</span>
                        <span
                            v-if="hasNewPosts(board.boardUrl, board.visitedAt)"
                            class="ml-1.5 h-2 w-2 rounded-full bg-[var(--nv-accent)]"
                            aria-hidden="true"
                        />
                        </router-link>
                        <button type="button" class="recent-board-remove" @click.stop="removeRecentBoard(board.boardUrl)"
                            :aria-label="$t('layout.recentBoards.removeAria', { name: board.boardName })">
                            <X class="h-3 w-3" aria-hidden="true" />
                        </button>
                    </div>
                </div>
                <button v-if="verifiedRecentBoards.length > 1" type="button" class="recent-boards-clear" @click="clearRecentBoards">
                    {{ $t('layout.recentBoards.clear') }}
                </button>
            </div>
        </div>
    </div>
</template>

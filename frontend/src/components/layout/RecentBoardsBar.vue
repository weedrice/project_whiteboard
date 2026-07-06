<script setup lang="ts">
import { computed } from 'vue'
import { useRecentBoards } from '@/composables/useRecentBoards'
import { X } from 'lucide-vue-next'
import { getOptimizedBoardIconUrl, handleImageError } from '@/utils/image'
import { encodePathSegment } from '@/utils/urlPath'

const { recentBoards, removeRecentBoard, clearRecentBoards } = useRecentBoards()

const hasBoards = computed(() => recentBoards.value.length > 0)
</script>

<template>
    <div v-if="hasBoards" class="recent-boards-bar">
        <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
            <div class="recent-boards-inner">
                <span class="recent-boards-label">{{ $t('layout.recentBoards.title') }}</span>
                <div class="recent-boards-list">
                    <div v-for="board in recentBoards" :key="board.boardUrl" class="recent-board-chip">
                        <router-link :to="`/board/${encodePathSegment(board.boardUrl)}`" class="recent-board-link">
                        <img v-if="board.iconUrl" :src="getOptimizedBoardIconUrl(board.iconUrl, 24)"
                            class="recent-board-icon" alt="" @error="handleImageError($event)" />
                        <span v-else class="recent-board-icon-fallback">{{ board.boardName[0] }}</span>
                        <span class="recent-board-name">{{ board.boardName }}</span>
                        </router-link>
                        <button type="button" class="recent-board-remove" @click.stop="removeRecentBoard(board.boardUrl)"
                            :aria-label="$t('layout.recentBoards.removeAria', { name: board.boardName })">
                            <X class="h-3 w-3" aria-hidden="true" />
                        </button>
                    </div>
                </div>
                <button v-if="recentBoards.length > 1" type="button" class="recent-boards-clear" @click="clearRecentBoards">
                    {{ $t('layout.recentBoards.clear') }}
                </button>
            </div>
        </div>
    </div>
</template>

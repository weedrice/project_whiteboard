<script setup lang="ts">
import { computed } from 'vue'
import { Keyboard } from 'lucide-vue-next'
import { useKeyboardStore } from '@/stores/keyboard'
import { useAuthStore } from '@/stores/auth'
import BaseModal from '@/components/common/ui/BaseModal.vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const keyboardStore = useKeyboardStore()
const authStore = useAuthStore()

const isOpen = computed(() => keyboardStore.isShortcutsModalOpen)

const close = () => {
    keyboardStore.closeShortcutsModal()
}

// 단축키 그룹 정의
const shortcutGroups = computed(() => [
    {
        title: t('layout.shortcuts.global'),
        shortcuts: [
            { key: 'Shift+/', description: t('layout.shortcuts.help') },
            { key: 'H', description: t('layout.shortcuts.home') },
            { key: 'D', description: t('layout.shortcuts.darkMode') },
            { key: 'B', description: t('layout.shortcuts.allBoards') },
            { key: 'Shift+B', description: t('layout.shortcuts.allBoardsPage') },
            ...(authStore.isAuthenticated ? [
                { key: 'S', description: t('layout.shortcuts.subscribedBoards') },
                { key: 'M', description: t('layout.shortcuts.myPage') },
                { key: 'Alt+N', description: t('layout.shortcuts.notifications') },
                { key: 'Q', description: t('layout.shortcuts.logout') },
            ] : []),
        ],
    },
    {
        title: t('layout.shortcuts.dropdown'),
        shortcuts: [
            { key: '1-9, 0', description: t('layout.shortcuts.selectItem') },
            { key: 'Esc', description: t('layout.shortcuts.closeDropdown') },
        ],
    },
    {
        title: t('layout.shortcuts.boardList'),
        shortcuts: [
            { key: ']', description: t('layout.shortcuts.nextPage') },
            { key: '[', description: t('layout.shortcuts.prevPage') },
            { key: 'Shift+]', description: t('layout.shortcuts.lastPage') },
            { key: 'Shift+[', description: t('layout.shortcuts.firstPage') },
            { key: 'N', description: t('layout.shortcuts.write') },
            { key: 'F', description: t('layout.shortcuts.subscribe') },
            { key: '/', description: t('layout.shortcuts.focusSearch') },
        ],
    },
    {
        title: t('layout.shortcuts.postDetail'),
        shortcuts: [
            { key: 'C', description: t('layout.shortcuts.comments') },
            { key: 'U', description: t('layout.shortcuts.toList') },
            { key: 'L', description: t('layout.shortcuts.like') },
            { key: 'Shift+S', description: t('layout.shortcuts.scrap') },
            { key: 'Y', description: t('layout.shortcuts.copyUrl') },
            { key: 'Shift+Y', description: t('layout.shortcuts.share') },
            { key: 'E', description: t('layout.shortcuts.edit') },
        ],
    },
    {
        title: t('layout.shortcuts.writeEdit'),
        shortcuts: [
            { key: 'Ctrl+Enter', description: t('layout.shortcuts.submit') },
            { key: 'Esc', description: t('layout.shortcuts.cancel') },
        ],
    },
    {
        title: t('layout.shortcuts.mypageTabs'),
        shortcuts: [
            { key: ']', description: t('layout.shortcuts.nextTab') },
            { key: '[', description: t('layout.shortcuts.prevTab') },
        ],
    },
])
</script>

<template>
    <BaseModal :isOpen="isOpen" :title="$t('layout.shortcuts.title')" @close="close" size="full">
        <!-- PC: 가로 넓은 그리드, 모바일: 세로 -->
        <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
            <!-- 왼쪽 열: 전역 + 드롭다운 + 마이페이지 탭 -->
            <div class="space-y-6">
                <div v-for="group in shortcutGroups.slice(0, 2)" :key="group.title">
                    <h3 class="text-sm font-semibold nv-title mb-3 flex items-center">
                        <Keyboard class="w-4 h-4 mr-2 nv-accent-text" />
                        {{ group.title }}
                    </h3>
                    <div class="space-y-1.5">
                        <div v-for="shortcut in group.shortcuts" :key="shortcut.key"
                            class="flex items-center justify-between py-1.5 px-3 nv-surface-muted rounded-md">
                            <span class="text-sm nv-text-muted">{{ shortcut.description }}</span>
                            <kbd
                                class="ml-2 px-2 py-0.5 text-xs font-semibold nv-text nv-surface-muted border nv-border rounded shadow-sm whitespace-nowrap">
                                {{ shortcut.key }}
                            </kbd>
                        </div>
                    </div>
                </div>
                <div v-if="shortcutGroups[5]" :key="shortcutGroups[5].title">
                    <h3 class="text-sm font-semibold nv-title mb-3 flex items-center">
                        <Keyboard class="w-4 h-4 mr-2 nv-accent-text" />
                        {{ shortcutGroups[5].title }}
                    </h3>
                    <div class="space-y-1.5">
                        <div v-for="shortcut in shortcutGroups[5].shortcuts" :key="shortcut.key"
                            class="flex items-center justify-between py-1.5 px-3 nv-surface-muted rounded-md">
                            <span class="text-sm nv-text-muted">{{ shortcut.description }}</span>
                            <kbd
                                class="ml-2 px-2 py-0.5 text-xs font-semibold nv-text nv-surface-muted border nv-border rounded shadow-sm whitespace-nowrap">
                                {{ shortcut.key }}
                            </kbd>
                        </div>
                    </div>
                </div>
            </div>

            <!-- 중앙 열: 스페이스 목록 + 글쓰기/수정 -->
            <div class="space-y-6">
                <div v-for="group in [shortcutGroups[2], shortcutGroups[4]]" :key="group.title">
                    <h3 class="text-sm font-semibold nv-title mb-3 flex items-center">
                        <Keyboard class="w-4 h-4 mr-2 nv-accent-text" />
                        {{ group.title }}
                    </h3>
                    <div class="space-y-1.5">
                        <div v-for="shortcut in group.shortcuts" :key="shortcut.key"
                            class="flex items-center justify-between py-1.5 px-3 nv-surface-muted rounded-md">
                            <span class="text-sm nv-text-muted">{{ shortcut.description }}</span>
                            <kbd
                                class="ml-2 px-2 py-0.5 text-xs font-semibold nv-text nv-surface-muted border nv-border rounded shadow-sm whitespace-nowrap">
                                {{ shortcut.key }}
                            </kbd>
                        </div>
                    </div>
                </div>
            </div>

            <!-- 오른쪽 열: 게시글 상세 -->
            <div class="space-y-6">
                <div>
                    <h3 class="text-sm font-semibold nv-title mb-3 flex items-center">
                        <Keyboard class="w-4 h-4 mr-2 nv-accent-text" />
                        {{ shortcutGroups[3].title }}
                    </h3>
                    <div class="space-y-1.5">
                        <div v-for="shortcut in shortcutGroups[3].shortcuts" :key="shortcut.key"
                            class="flex items-center justify-between py-1.5 px-3 nv-surface-muted rounded-md">
                            <span class="text-sm nv-text-muted">{{ shortcut.description }}</span>
                            <kbd
                                class="ml-2 px-2 py-0.5 text-xs font-semibold nv-text nv-surface-muted border nv-border rounded shadow-sm whitespace-nowrap">
                                {{ shortcut.key }}
                            </kbd>
                        </div>
                    </div>
                </div>
            </div>
        </div>

        <template #footer>
            <div class="flex justify-end items-center gap-4">
                <span class="text-xs nv-text-subtle">
                    {{ $t('layout.shortcuts.openAnytime') }}
                </span>
                <button @click="close"
                    class="px-4 py-2 text-sm font-medium nv-text nv-surface-muted nv-hover-surface rounded-md transition-colors">
                    {{ $t('common.close') }}
                </button>
            </div>
        </template>
    </BaseModal>
</template>

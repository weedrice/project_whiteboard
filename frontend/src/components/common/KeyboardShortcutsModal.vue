<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import BaseModal from '@/components/common/ui/BaseModal.vue'
import KeyboardShortcutGroup from '@/components/common/KeyboardShortcutGroup.vue'
import {
    buildKeyboardShortcutColumns,
    buildKeyboardShortcutGroups,
} from '@/components/common/keyboardShortcutsModel'
import { useAuthStore } from '@/stores/auth'
import { useKeyboardStore } from '@/stores/keyboard'

const { t } = useI18n()
const keyboardStore = useKeyboardStore()
const authStore = useAuthStore()

const isOpen = computed(() => keyboardStore.isShortcutsModalOpen)
const shortcutGroups = computed(() => buildKeyboardShortcutGroups(t, authStore.isAuthenticated))
const shortcutColumns = computed(() => buildKeyboardShortcutColumns(shortcutGroups.value))

function close() {
    keyboardStore.closeShortcutsModal()
}
</script>

<template>
    <BaseModal :isOpen="isOpen" :title="$t('layout.shortcuts.title')" @close="close" size="full">
        <div class="grid grid-cols-1 gap-6 lg:grid-cols-3">
            <div
                v-for="(column, columnIndex) in shortcutColumns"
                :key="columnIndex"
                class="space-y-6"
            >
                <KeyboardShortcutGroup
                    v-for="group in column"
                    :key="group.title"
                    :group="group"
                />
            </div>
        </div>

        <template #footer>
            <div class="flex items-center justify-end gap-4">
                <span class="text-xs nv-text-subtle">
                    {{ $t('layout.shortcuts.openAnytime') }}
                </span>
                <button
                    type="button"
                    class="nv-focus-ring min-h-11 rounded-md px-4 py-2 text-sm font-medium transition-colors nv-hover-surface nv-surface-muted nv-text"
                    @click="close"
                >
                    {{ $t('common.close') }}
                </button>
            </div>
        </template>
    </BaseModal>
</template>

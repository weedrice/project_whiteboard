<script setup lang="ts">
import { Keyboard } from 'lucide-vue-next'
import type { KeyboardShortcutGroup } from '@/components/common/keyboardShortcutsModel'

defineProps<{
    group: KeyboardShortcutGroup
}>()
</script>

<template>
    <section>
        <h3 class="mb-3 flex items-center text-sm font-semibold nv-title">
            <Keyboard class="mr-2 h-4 w-4 nv-accent-text" />
            {{ group.title }}
        </h3>
        <div class="space-y-1.5">
            <div
                v-for="shortcut in group.shortcuts"
                :key="shortcut.key"
                class="flex items-center justify-between rounded-md px-3 py-1.5 nv-surface-muted"
            >
                <span class="text-sm nv-text-muted">{{ shortcut.description }}</span>
                <kbd class="ml-2 whitespace-nowrap rounded border px-2 py-0.5 text-xs font-semibold shadow-sm nv-border nv-surface-muted nv-text">
                    {{ shortcut.key }}
                </kbd>
            </div>
        </div>
    </section>
</template>

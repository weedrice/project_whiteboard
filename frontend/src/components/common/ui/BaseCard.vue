<script setup lang="ts">
withDefaults(defineProps<{
    noPadding?: boolean
}>(), {
    noPadding: false
})
</script>

<template>
    <div class="nv-surface shadow overflow-hidden sm:rounded-lg transition-colors duration-200">
        <div :class="{ 'px-4 py-5 sm:p-6': !noPadding }">
            <slot />
        </div>
    </div>
</template>

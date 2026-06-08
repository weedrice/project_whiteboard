<script setup lang="ts">
withDefaults(defineProps<{
    width?: string
    height?: string
    rounded?: string
    className?: string
}>(), {
    width: '100%',
    height: '1rem',
    rounded: 'rounded-md',
    className: ''
})
</script>

<template>
    <div :class="['nv-surface-muted animate-pulse', rounded, className]" :style="{ width, height }"></div>
</template>

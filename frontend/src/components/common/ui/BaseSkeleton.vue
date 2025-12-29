<script setup>
defineProps({
    width: {
        type: String,
        default: '100%'
    },
    height: {
        type: String,
        default: '1rem'
    },
    rounded: {
        type: String,
        default: 'rounded-md'
    },
    className: {
        type: String,
        default: ''
    }
})
</script>

<template>
    <div :class="['bg-gray-200 dark:bg-gray-700 animate-pulse', rounded, className]" :style="{ width, height }"></div>
</template>

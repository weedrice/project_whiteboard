<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{
    variant?: 'primary' | 'secondary' | 'success' | 'danger' | 'warning' | 'info' | 'gray'
    size?: 'sm' | 'md' | 'lg'
    rounded?: boolean
}>(), {
    variant: 'primary',
    size: 'md',
    rounded: true
})

const classes = computed(() => {
    const baseClasses = 'inline-flex items-center font-semibold'

    const sizeClasses = {
        sm: 'px-2 py-0.5 text-xs',
        md: 'px-2.5 py-0.5 text-sm',
        lg: 'px-3 py-0.5 text-base'
    }

    const variantClasses = {
        primary: 'bg-indigo-100 text-indigo-800 dark:bg-indigo-900 dark:text-indigo-200',
        secondary: 'bg-purple-100 text-purple-800 dark:bg-purple-900 dark:text-purple-200',
        success: 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200',
        danger: 'bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-200',
        warning: 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900 dark:text-yellow-200',
        info: 'bg-blue-100 text-blue-800 dark:bg-blue-900 dark:text-blue-200',
        gray: 'bg-gray-100 text-gray-800 dark:bg-gray-700 dark:text-gray-300'
    }

    const roundedClass = props.rounded ? 'rounded-full' : 'rounded'

    return `${baseClasses} ${sizeClasses[props.size]} ${variantClasses[props.variant]} ${roundedClass}`
})
</script>

<template>
    <span :class="classes">
        <slot />
    </span>
</template>

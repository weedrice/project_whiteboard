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
        primary: 'nv-status-info',
        secondary: 'nv-surface-muted nv-text-muted',
        success: 'nv-status-success',
        danger: 'nv-status-danger',
        warning: 'nv-status-warning',
        info: 'nv-status-info',
        gray: 'nv-surface-muted nv-text-muted'
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

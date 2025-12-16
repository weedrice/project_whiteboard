<script setup lang="ts">
withDefaults(defineProps<{
    size?: 'sm' | 'md' | 'lg' | 'xl'
    color?: string
}>(), {
    size: 'md',
    color: 'text-indigo-600 dark:text-indigo-400'
})

const sizeClasses = {
    sm: 'h-4 w-4 border-2',
    md: 'h-8 w-8 border-2',
    lg: 'h-12 w-12 border-4',
    xl: 'h-16 w-16 border-4'
}
</script>

<template>
    <div class="flex justify-center items-center">
        <div class="animate-spin rounded-full border-t-transparent"
            :class="[sizeClasses[size], color, 'border-current']" role="status">
            <span class="sr-only">Loading...</span>
        </div>
    </div>
</template>

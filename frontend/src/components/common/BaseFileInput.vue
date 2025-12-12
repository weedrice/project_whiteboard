<script setup lang="ts">
import { ref } from 'vue'

const props = defineProps<{
    modelValue?: File | null
    accept?: string
    label?: string
    error?: string
    placeholder?: string
    disabled?: boolean
}>()

const emit = defineEmits<{
    (e: 'update:modelValue', file: File | null): void
    (e: 'change', event: Event): void
}>()

const fileInput = ref<HTMLInputElement | null>(null)

const handleChange = (event: Event) => {
    const target = event.target as HTMLInputElement
    const file = target.files?.[0] || null
    emit('update:modelValue', file)
    emit('change', event)
}

const handleClick = () => {
    if (!props.disabled && fileInput.value) {
        fileInput.value.click()
    }
}
</script>

<template>
    <div class="w-full">
        <label v-if="label" class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
            {{ label }}
        </label>
        <div @click="handleClick"
            class="relative flex items-center justify-center w-full px-4 py-2 border border-gray-300 dark:border-gray-600 rounded-md shadow-sm bg-white dark:bg-gray-700 text-sm font-medium text-gray-700 dark:text-gray-200 hover:bg-gray-50 dark:hover:bg-gray-600 focus-within:ring-2 focus-within:ring-offset-2 focus-within:ring-indigo-500 cursor-pointer transition-colors duration-200"
            :class="{ 'opacity-50 cursor-not-allowed': disabled, 'border-red-300 text-red-900 placeholder-red-300 focus:ring-red-500 focus:border-red-500': error }">
            <span v-if="modelValue" class="truncate">{{ modelValue.name }}</span>
            <span v-else class="text-gray-500 dark:text-gray-400">{{ placeholder || $t('common.chooseFile') }}</span>
            <input ref="fileInput" type="file" class="sr-only" :accept="accept" :disabled="disabled"
                @change="handleChange" />
        </div>
        <p v-if="error" class="mt-2 text-sm text-red-600" id="email-error">{{ error }}</p>
    </div>
</template>

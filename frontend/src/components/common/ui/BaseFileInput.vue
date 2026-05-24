<script setup lang="ts">
withDefaults(defineProps<{
    id?: string
    modelValue?: File | null
    accept?: string
    label?: string
    error?: string
    placeholder?: string
    disabled?: boolean
}>(), {
    id: () => `file-input-${Math.random().toString(36).substr(2, 9)}`,
    modelValue: null,
    accept: undefined,
    label: '',
    error: '',
    placeholder: '',
    disabled: false,
})

const emit = defineEmits<{
    (e: 'update:modelValue', file: File | null): void
    (e: 'change', event: Event): void
}>()

const handleChange = (event: Event) => {
    const target = event.target as HTMLInputElement
    const file = target.files?.[0] || null
    emit('update:modelValue', file)
    emit('change', event)
}
</script>

<template>
    <div class="w-full">
        <label v-if="label" :for="id" class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
            {{ label }}
        </label>
        <label :for="id"
            class="relative flex items-center justify-center w-full px-4 py-2 border border-gray-300 dark:border-gray-600 rounded-md shadow-sm bg-white dark:bg-gray-700 text-sm font-medium text-gray-700 dark:text-gray-200 hover:bg-gray-50 dark:hover:bg-gray-600 focus-within:ring-2 focus-within:ring-offset-2 focus-within:ring-indigo-500 cursor-pointer transition-colors duration-200"
            :class="{ 'opacity-50 cursor-not-allowed': disabled, 'border-red-300 text-red-900 placeholder-red-300 focus:ring-red-500 focus:border-red-500': error }">
            <span v-if="modelValue" class="truncate">{{ modelValue.name }}</span>
            <span v-else class="text-gray-500 dark:text-gray-400">{{ placeholder || $t('common.chooseFile') }}</span>
            <input :id="id" type="file" class="sr-only" :accept="accept" :disabled="disabled"
                :aria-invalid="error ? 'true' : undefined"
                :aria-describedby="error ? `${id}-error` : undefined"
                @change="handleChange" />
        </label>
        <p v-if="error" class="mt-2 text-sm text-red-600" :id="`${id}-error`" role="alert">{{ error }}</p>
    </div>
</template>

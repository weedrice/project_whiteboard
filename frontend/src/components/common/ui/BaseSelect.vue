<template>
    <div class="flex flex-col gap-1" :class="$attrs.class">
        <label v-if="label && !hideLabel" :for="id" class="text-sm font-medium text-gray-700 dark:text-gray-200"
            :class="labelClass">
            {{ label }}
        </label>
        <select v-bind="{ ...$attrs, class: undefined }" :id="id" :value="modelValue" @change="updateValue"
            :disabled="disabled" class="input-base" :class="[
                { 'border-red-500 focus:border-red-500 focus:ring-red-500': error },
                inputClass
            ]">
            <slot></slot>
        </select>
        <p v-if="error" class="text-sm text-red-600 dark:text-red-400">{{ error }}</p>
    </div>
</template>

<script setup lang="ts">
defineOptions({
    inheritAttrs: false
})

const props = withDefaults(defineProps<{
    id?: string
    label?: string
    modelValue?: string | number
    disabled?: boolean
    error?: string
    labelClass?: string
    inputClass?: string
    hideLabel?: boolean
}>(), {
    id: () => `select-${Math.random().toString(36).substr(2, 9)}`,
    label: '',
    modelValue: '',
    disabled: false,
    error: '',
    labelClass: '',
    inputClass: '',
    hideLabel: false
})

const emit = defineEmits<{
    (e: 'update:modelValue', value: string | number): void
}>()

const updateValue = (event: Event) => {
    const target = event.target as HTMLSelectElement
    emit('update:modelValue', target.value)
}
</script>

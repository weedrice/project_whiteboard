<template>
    <div class="flex items-start" :class="$attrs.class">
        <div class="flex items-center h-5">
            <input :id="id" type="checkbox" :checked="checked" @change="updateValue" :disabled="disabled"
                class="focus:ring-indigo-500 h-4 w-4 text-indigo-600 border-gray-300 rounded cursor-pointer bg-[var(--nv-surface)] border-[var(--nv-border)]"
                :class="inputClass" />
        </div>
        <div class="ml-3 text-sm">
            <label :for="id" class="font-medium nv-text-muted cursor-pointer" :class="labelClass">
                {{ label }}
            </label>
            <p v-if="description" class="nv-text-subtle">{{ description }}</p>
        </div>
    </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

defineOptions({
    inheritAttrs: false
})

const props = withDefaults(defineProps<{
    id?: string
    label?: string
    description?: string
    /** Boolean for single checkbox, or pass array + value for multi-select (checked = value in array) */
    modelValue?: boolean | unknown[]
    /** When set, modelValue is treated as array of selected values (multi-select mode) */
    value?: unknown
    disabled?: boolean
    labelClass?: string
    inputClass?: string
}>(), {
    id: () => `checkbox-${Math.random().toString(36).substr(2, 9)}`,
    label: '',
    description: '',
    modelValue: false,
    value: undefined,
    disabled: false,
    labelClass: '',
    inputClass: ''
})

const emit = defineEmits<{
    (e: 'update:modelValue', value: boolean | unknown[]): void
}>()

const isArrayMode = computed(() => props.value !== undefined && Array.isArray(props.modelValue))

const checked = computed(() => {
    if (isArrayMode.value && Array.isArray(props.modelValue)) {
        return props.modelValue.includes(props.value)
    }
    return Boolean(props.modelValue)
})

const updateValue = (event: Event) => {
    const target = event.target as HTMLInputElement
    if (isArrayMode.value && Array.isArray(props.modelValue)) {
        const arr = [...props.modelValue]
        const idx = arr.indexOf(props.value)
        if (target.checked) {
            if (idx === -1) arr.push(props.value)
        } else {
            if (idx !== -1) arr.splice(idx, 1)
        }
        emit('update:modelValue', arr)
    } else {
        emit('update:modelValue', target.checked)
    }
}
</script>

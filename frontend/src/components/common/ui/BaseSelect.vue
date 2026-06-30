<template>
    <div class="flex flex-col gap-1" :class="$attrs.class">
        <label
            v-if="label"
            :for="selectId"
            :class="hideLabel ? 'sr-only' : ['text-sm font-medium nv-text', labelClass]"
        >
            {{ label }}
        </label>
        <select v-bind="{ ...$attrs, class: undefined }" :id="selectId" :value="modelValue" @change="updateValue"
            :disabled="disabled"
            :aria-invalid="error ? 'true' : undefined"
            :aria-describedby="error ? `${selectId}-error` : undefined"
            class="input-base" :class="[
                { 'is-invalid': error },
                inputClass
            ]">
            <option v-if="placeholder" value="" disabled selected>{{ placeholder }}</option>
            <template v-if="options && options.length > 0">
                <option v-for="option in normalizedOptions" :key="option.value" :value="option.value">
                    {{ option.label }}
                </option>
            </template>
            <slot v-else></slot>
        </select>
        <p v-if="error" :id="`${selectId}-error`" class="text-sm nv-form-error" role="alert">{{ error }}</p>
    </div>
</template>

<script setup lang="ts">
import { computed, useId } from 'vue'

defineOptions({
    inheritAttrs: false
})

interface Option {
    label: string
    value: string | number
}

const props = withDefaults(defineProps<{
    id?: string
    label?: string
    modelValue?: string | number
    disabled?: boolean
    error?: string
    labelClass?: string
    inputClass?: string
    hideLabel?: boolean
    options?: (string | number | Option)[]
    placeholder?: string
}>(), {
    label: '',
    modelValue: '',
    disabled: false,
    error: '',
    labelClass: '',
    inputClass: '',
    hideLabel: false,
    options: () => [],
    placeholder: ''
})

const generatedId = useId()
const selectId = computed(() => props.id ?? generatedId)

const emit = defineEmits<{
    (e: 'update:modelValue', value: string | number): void
}>()

const normalizedOptions = computed(() => {
    if (!props.options) return []
    return props.options.map(opt => {
        if (typeof opt === 'object' && opt !== null) {
            return opt as Option
        }
        return { label: String(opt), value: opt }
    })
})

const updateValue = (event: Event) => {
    const target = event.target as HTMLSelectElement
    emit('update:modelValue', target.value)
}
</script>

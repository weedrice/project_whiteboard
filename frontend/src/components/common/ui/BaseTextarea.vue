<template>
    <div class="flex flex-col gap-1" :class="$attrs.class">
        <label
            v-if="label"
            :for="textareaId"
            :class="hideLabel ? 'sr-only' : ['text-sm font-medium nv-text', labelClass]"
        >
            {{ label }}
        </label>
        <textarea v-bind="{ ...$attrs, class: undefined }" :id="textareaId" :value="modelValue" @input="updateValue" @blur="$emit('blur', $event)"
            :disabled="disabled"
            :aria-invalid="error ? 'true' : undefined"
            :aria-describedby="describedBy"
            class="input-base" :class="[
                { 'is-invalid': error },
                inputClass
            ]"></textarea>
        <p v-if="error" :id="`${textareaId}-error`" class="text-sm nv-form-error" role="alert">{{ error }}</p>
    </div>
</template>

<script setup lang="ts">
import { computed, useAttrs, useId } from 'vue'

defineOptions({
    inheritAttrs: false
})

const props = withDefaults(defineProps<{
    id?: string
    label?: string
    modelValue?: string
    disabled?: boolean
    error?: string
    labelClass?: string
    inputClass?: string
    hideLabel?: boolean
}>(), {
    label: '',
    modelValue: '',
    disabled: false,
    error: '',
    labelClass: '',
    inputClass: '',
    hideLabel: false
})

const generatedId = useId()
const textareaId = computed(() => props.id ?? generatedId)
const attrs = useAttrs()
const describedBy = computed(() => [
    attrs['aria-describedby'],
    props.error ? `${textareaId.value}-error` : undefined,
].filter(Boolean).join(' ') || undefined)

const emit = defineEmits<{
    (e: 'update:modelValue', value: string): void
    (e: 'blur', event: FocusEvent): void
}>()

const updateValue = (event: Event) => {
    const target = event.target as HTMLTextAreaElement
    emit('update:modelValue', target.value)
}
</script>

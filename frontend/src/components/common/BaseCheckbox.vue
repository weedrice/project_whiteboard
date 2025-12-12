<template>
    <div class="flex items-start" :class="$attrs.class">
        <div class="flex items-center h-5">
            <input :id="id" type="checkbox" :checked="modelValue" @change="updateValue" :disabled="disabled"
                class="focus:ring-indigo-500 h-4 w-4 text-indigo-600 border-gray-300 rounded cursor-pointer dark:bg-gray-700 dark:border-gray-600"
                :class="inputClass" />
        </div>
        <div class="ml-3 text-sm">
            <label :for="id" class="font-medium text-gray-700 dark:text-gray-300 cursor-pointer" :class="labelClass">
                {{ label }}
            </label>
            <p v-if="description" class="text-gray-500 dark:text-gray-400">{{ description }}</p>
        </div>
    </div>
</template>

<script setup lang="ts">
defineOptions({
    inheritAttrs: false
})

const props = withDefaults(defineProps<{
    id?: string
    label?: string
    description?: string
    modelValue?: boolean
    disabled?: boolean
    labelClass?: string
    inputClass?: string
}>(), {
    id: () => `checkbox-${Math.random().toString(36).substr(2, 9)}`,
    label: '',
    description: '',
    modelValue: false,
    disabled: false,
    labelClass: '',
    inputClass: ''
})

const emit = defineEmits<{
    (e: 'update:modelValue', value: boolean): void
}>()

const updateValue = (event: Event) => {
    const target = event.target as HTMLInputElement
    emit('update:modelValue', target.checked)
}
</script>

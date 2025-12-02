<template>
  <div class="space-y-2">
    <div v-if="!readOnly" class="flex flex-wrap gap-2 items-center">
      <div class="relative">
        <input
          v-model="newTag"
          @keydown.enter.prevent="addTag"
          @keydown.comma.prevent="addTag"
          type="text"
          :placeholder="$t('board.tags.placeholder')"
          class="px-3 py-1 border border-gray-300 rounded-full text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
        />
      </div>
      <span class="text-xs text-gray-500">{{ $t('board.tags.help') }}</span>
    </div>

    <div class="flex flex-wrap gap-2">
      <span
        v-for="(tag, index) in modelValue"
        :key="index"
        class="inline-flex items-center px-2.5 py-0.5 rounded-full text-sm font-medium bg-blue-100 text-blue-800"
      >
        #{{ tag }}
        <button
          v-if="!readOnly"
          @click="removeTag(index)"
          type="button"
          class="ml-1.5 inline-flex items-center justify-center h-4 w-4 rounded-full text-blue-400 hover:bg-blue-200 hover:text-blue-500 focus:outline-none"
        >
          <span class="sr-only">{{ $t('board.tags.remove') }}</span>
          <svg class="h-2 w-2" stroke="currentColor" fill="none" viewBox="0 0 8 8">
            <path stroke-linecap="round" stroke-width="1.5" d="M1 1l6 6m0-6L1 7" />
          </svg>
        </button>
      </span>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'

const props = defineProps({
  modelValue: {
    type: Array,
    default: () => []
  },
  readOnly: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:modelValue'])

const newTag = ref('')

const addTag = () => {
  const tag = newTag.value.trim()
  if (tag && !props.modelValue.includes(tag)) {
    emit('update:modelValue', [...props.modelValue, tag])
  }
  newTag.value = ''
}

const removeTag = (index) => {
  const newTags = [...props.modelValue]
  newTags.splice(index, 1)
  emit('update:modelValue', newTags)
}
</script>

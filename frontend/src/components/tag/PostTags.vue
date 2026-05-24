<template>
  <div :class="compact && readOnly ? 'space-y-1' : 'space-y-2'">
    <div v-if="!readOnly" class="flex flex-wrap gap-2 items-center">
      <div class="relative flex-1 min-w-0 flex items-center gap-2">
        <label :for="inputId" class="sr-only">{{ $t('board.tags.placeholder') }}</label>
        <input :id="inputId" v-model="newTag" name="postTag" autocomplete="off" @keydown.enter.prevent="addTag" type="text"
          :placeholder="$t('board.tags.placeholder')"
          class="flex-1 min-w-0 px-3 py-1 border border-gray-300 dark:border-gray-600 rounded-full text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent bg-white dark:bg-gray-700 text-gray-900 dark:text-white placeholder-gray-500 dark:placeholder-gray-400" />
        <button type="button" @click="addTag"
          class="sm:hidden flex-shrink-0 px-3 py-1.5 text-xs font-medium rounded-full border border-indigo-600 dark:border-indigo-500 text-indigo-600 dark:text-indigo-400 bg-transparent hover:bg-indigo-50 dark:hover:bg-indigo-900/30 focus:outline-none focus:ring-2 focus:ring-indigo-500">
          {{ $t('common.add') }}
        </button>
      </div>
      <span class="hidden sm:inline text-xs text-gray-500 dark:text-gray-400">{{ $t('board.tags.help') }}</span>
    </div>

    <div class="flex flex-wrap" :class="compact && readOnly ? 'gap-x-2 gap-y-1' : 'gap-2'">
      <template v-for="{ tag, index, key } in tagItems" :key="key">
        <button v-if="readOnly && boardUrl" type="button" @click="handleTagClick(tag)"
          :class="[
            'inline-flex items-center rounded-full font-medium transition-colors cursor-pointer',
            compact
              ? 'px-2.5 py-1 text-[11px] text-slate-700 bg-slate-100 border border-slate-200 hover:bg-slate-200 dark:text-slate-200 dark:bg-slate-800 dark:border-slate-700 dark:hover:bg-slate-700'
              : 'px-2 py-0.5 text-xs sm:text-sm bg-blue-100 dark:bg-blue-900 text-blue-800 dark:text-blue-200 hover:bg-blue-200 dark:hover:bg-blue-800'
          ]">
          #{{ tag }}
        </button>
        <span v-else
          :class="[
            'inline-flex items-center rounded-full font-medium',
            compact && readOnly
              ? 'px-2.5 py-1 text-[11px] text-slate-700 bg-slate-100 border border-slate-200 dark:text-slate-200 dark:bg-slate-800 dark:border-slate-700'
              : 'px-2 py-0.5 sm:px-2.5 sm:py-0.5 text-xs sm:text-sm bg-blue-100 dark:bg-blue-900 text-blue-800 dark:text-blue-200'
          ]">
          #{{ tag }}
          <button v-if="!readOnly" @click="removeTag(index)" type="button"
            class="ml-1 sm:ml-1.5 inline-flex items-center justify-center h-3 w-3 sm:h-4 sm:w-4 rounded-full text-blue-400 dark:text-blue-300 hover:bg-blue-200 dark:hover:bg-blue-800 hover:text-blue-500 dark:hover:text-blue-100 focus:outline-none">
            <span class="sr-only">{{ $t('board.tags.remove') }}</span>
            <svg class="h-2 w-2" stroke="currentColor" fill="none" viewBox="0 0 8 8">
              <path stroke-linecap="round" stroke-width="1.5" d="M1 1l6 6m0-6L1 7" />
            </svg>
          </button>
        </span>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'

const props = withDefaults(defineProps<{
  modelValue: string[]
  readOnly?: boolean
  boardUrl?: string | number
  compact?: boolean
  inputId?: string
}>(), {
  modelValue: () => [],
  readOnly: false,
  boardUrl: undefined,
  compact: false,
  inputId: 'post-tags-input'
})

const emit = defineEmits<{
  (e: 'update:modelValue', tags: string[]): void
  (e: 'tag-click', tag: string): void
}>()

const newTag = ref('')
const tagItems = computed(() => {
  const counts = new Map<string, number>()

  return props.modelValue.map((tag, index) => {
    const occurrence = (counts.get(tag) ?? 0) + 1
    counts.set(tag, occurrence)

    return {
      tag,
      index,
      key: `${tag}:${occurrence}`,
    }
  })
})

const addTag = () => {
  const tag = newTag.value.trim()
  if (tag && !props.modelValue.includes(tag)) {
    emit('update:modelValue', [...props.modelValue, tag])
  }
  newTag.value = ''
}

const removeTag = (index: number) => {
  const newTags = [...props.modelValue]
  newTags.splice(index, 1)
  emit('update:modelValue', newTags)
}

const handleTagClick = (tag: string) => {
  emit('tag-click', tag)
}
</script>

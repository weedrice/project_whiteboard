<template>
  <div :class="compact && readOnly ? 'space-y-1' : 'space-y-2'">
    <div v-if="!readOnly" class="flex flex-wrap gap-2 items-center">
      <div class="relative flex-1 min-w-0 flex items-center gap-2">
        <BaseInput
          :id="inputId"
          v-model="newTag"
          name="postTag"
          autocomplete="off"
          type="text"
          :label="$t('board.tags.placeholder')"
          :placeholder="$t('board.tags.placeholder')"
          input-class="rounded-full py-1 text-sm"
          class="min-w-0 flex-1"
          hide-label
          @keydown.enter="handleTagKeydown"
        />
        <button type="button" @click="addTag"
          class="nv-touch-target sm:hidden flex-shrink-0 px-3 py-1.5 text-xs font-medium rounded-full border border-[var(--nv-accent)] nv-accent-text bg-transparent nv-hover-surface nv-focus-ring">
          {{ $t('common.add') }}
        </button>
      </div>
      <span class="hidden sm:inline text-xs nv-text-subtle">{{ $t('board.tags.help') }}</span>
    </div>

    <div class="flex flex-wrap" :class="compact && readOnly ? 'gap-x-2 gap-y-1' : 'gap-2'">
      <template v-for="{ tag, index, key } in tagItems" :key="key">
        <button v-if="readOnly && boardUrl" type="button" @click="handleTagClick(tag)"
          :class="[
            'inline-flex min-h-11 items-center rounded-full font-medium transition-colors cursor-pointer sm:min-h-0',
            compact
              ? 'px-2.5 py-1 text-[11px] nv-surface-muted nv-border border nv-hover-surface'
              : 'px-2 py-0.5 text-xs sm:text-sm nv-status-info nv-hover-surface'
          ]">
          #{{ tag }}
        </button>
        <span v-else
          :class="[
            'inline-flex items-center rounded-full font-medium',
            compact && readOnly
              ? 'px-2.5 py-1 text-[11px] nv-surface-muted nv-border border'
              : 'px-2 py-0.5 sm:px-2.5 sm:py-0.5 text-xs sm:text-sm nv-status-info'
          ]">
          #{{ tag }}
          <button v-if="!readOnly" @click="removeTag(index)" type="button"
            class="ml-1 inline-flex min-h-11 min-w-11 items-center justify-center rounded-full nv-accent-text nv-hover-surface nv-focus-ring sm:ml-1.5 sm:min-h-0 sm:min-w-0">
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
import { isComposingKeyboardEvent } from '@/utils/keyboard'
import BaseInput from '@/components/common/ui/BaseInput.vue'

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

const handleTagKeydown = (event: KeyboardEvent) => {
  if (isComposingKeyboardEvent(event)) return
  event.preventDefault()
  addTag()
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

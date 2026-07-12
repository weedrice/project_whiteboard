<script setup lang="ts">
import { nextTick, ref, type Component, type ComponentPublicInstance } from 'vue'

export interface SegmentedControlOption {
  value: string
  label: string
  icon?: Component
}

const props = withDefaults(defineProps<{
  modelValue: string
  options: SegmentedControlOption[]
  label: string
  variant?: 'joined' | 'underline' | 'pill'
  selectionMode?: 'pressed' | 'tab'
  disabled?: boolean
}>(), {
  variant: 'joined',
  selectionMode: 'pressed',
  disabled: false,
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
  change: [value: string]
}>()

function select(value: string) {
  if (props.disabled) return
  emit('update:modelValue', value)
  emit('change', value)
}

const optionButtons = ref<HTMLButtonElement[]>([])

function setOptionButton(element: Element | ComponentPublicInstance | null, index: number) {
  if (element instanceof HTMLButtonElement) {
    optionButtons.value[index] = element
    return
  }

  optionButtons.value.splice(index, 1)
}

function findSelectedIndex() {
  const selectedIndex = props.options.findIndex((option) => option.value === props.modelValue)
  return selectedIndex >= 0 ? selectedIndex : 0
}

async function selectAndFocus(index: number) {
  const option = props.options[index]
  if (!option) return

  select(option.value)
  await nextTick()
  optionButtons.value[index]?.focus()
}

function handleTabKeydown(event: KeyboardEvent) {
  if (props.selectionMode !== 'tab' || props.disabled || props.options.length === 0) return

  const currentIndex = findSelectedIndex()
  const lastIndex = props.options.length - 1
  let nextIndex: number | null = null

  switch (event.key) {
    case 'ArrowLeft':
    case 'ArrowUp':
      nextIndex = currentIndex <= 0 ? lastIndex : currentIndex - 1
      break
    case 'ArrowRight':
    case 'ArrowDown':
      nextIndex = currentIndex >= lastIndex ? 0 : currentIndex + 1
      break
    case 'Home':
      nextIndex = 0
      break
    case 'End':
      nextIndex = lastIndex
      break
    default:
      return
  }

  event.preventDefault()
  void selectAndFocus(nextIndex)
}
</script>

<template>
  <div
    :class="[
      variant === 'joined' && 'isolate inline-flex max-w-full rounded-lg sm:rounded-md shadow-sm w-full sm:w-auto',
      variant === 'underline' && 'flex border-b nv-border',
      variant === 'pill' && 'flex max-w-full flex-wrap items-center gap-1 rounded-[var(--nv-radius-xl)] border border-[var(--nv-line)] bg-[var(--nv-surface)] p-1',
    ]"
    :role="selectionMode === 'tab' ? 'tablist' : 'group'"
    :aria-label="label"
  >
    <button
      v-for="(option, index) in options"
      :key="option.value"
      :ref="(element) => setOptionButton(element, index)"
      type="button"
      :role="selectionMode === 'tab' ? 'tab' : undefined"
      :aria-selected="selectionMode === 'tab' ? modelValue === option.value : undefined"
      :aria-pressed="selectionMode === 'pressed' ? modelValue === option.value : undefined"
      :tabindex="selectionMode === 'tab' ? (modelValue === option.value ? 0 : -1) : undefined"
      :disabled="disabled"
      :class="[
        variant === 'joined' && [
          'min-w-0 flex-1 sm:flex-initial relative inline-flex items-center justify-center px-3 py-2.5 sm:py-2 text-center text-sm font-medium leading-tight whitespace-normal ring-1 ring-inset min-h-[44px] sm:min-h-0 focus:z-10',
          index === 0 && 'rounded-l-lg sm:rounded-l-md',
          index === options.length - 1 && 'rounded-r-lg sm:rounded-r-md',
          index > 0 && '-ml-px',
          modelValue === option.value
            ? 'bg-[var(--nv-accent)] border-[var(--nv-accent)] text-white hover:brightness-95'
            : 'nv-surface border-[var(--nv-border-strong)] nv-text nv-hover-surface',
        ],
        variant === 'underline' && [
          'min-w-0 flex-1 rounded-b-none border-b-2 px-4 py-2 inline-flex items-center justify-center text-center text-sm font-medium leading-tight whitespace-normal transition-colors',
          modelValue === option.value
            ? 'border-[var(--nv-accent)] text-[var(--nv-accent)]'
            : 'border-transparent nv-text-subtle hover:text-[var(--nv-text)]',
        ],
        variant === 'pill' && [
          'min-h-11 min-w-11 rounded-full px-3 py-2 text-center text-xs font-medium uppercase leading-tight tracking-[0.08em] whitespace-normal transition-colors touch-manipulation sm:min-h-0 sm:min-w-0 sm:py-1.5',
          modelValue === option.value
            ? 'bg-[var(--nv-ink)] text-[var(--nv-bg)]'
            : 'text-[var(--nv-ink-soft)] hover:text-[var(--nv-ink)]',
        ],
      ]"
      @click="select(option.value)"
      @keydown="handleTabKeydown"
    >
      <component v-if="option.icon" :is="option.icon" class="mr-2 h-4 w-4" />
      {{ option.label }}
    </button>
  </div>
</template>

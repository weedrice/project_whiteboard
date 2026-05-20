<script setup lang="ts">
import { nextTick, onMounted, ref, watch, type ComponentPublicInstance } from 'vue'
import { useI18n } from 'vue-i18n'

type SlashAction = 'image' | 'quote' | 'list' | 'link' | 'divider'

const props = defineProps<{
  actions: SlashAction[]
  activeIndex: number
}>()

const emit = defineEmits<{
  (e: 'select', action: SlashAction): void
  (e: 'move', direction: 1 | -1): void
  (e: 'set-active', index: number): void
  (e: 'close'): void
}>()

const { t } = useI18n()

const actionLabels: Record<SlashAction, string> = {
  image: 'board.writePost.toolbar.image',
  quote: 'board.writePost.toolbar.quote',
  list: 'board.writePost.toolbar.list',
  link: 'board.writePost.toolbar.link',
  divider: 'board.writePost.toolbar.divider',
}

const actionButtonRefs = ref<HTMLButtonElement[]>([])

function setActionButtonRef(element: Element | ComponentPublicInstance | null, index: number) {
  const button = element instanceof Element
    ? element
    : element?.$el
  if (button) {
    if (button instanceof HTMLButtonElement) {
      actionButtonRefs.value[index] = button
    }
  }
}

async function focusActiveItem() {
  await nextTick()
  actionButtonRefs.value[props.activeIndex]?.focus()
}

watch(() => props.activeIndex, () => {
  void focusActiveItem()
})

onMounted(() => {
  void focusActiveItem()
})

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'ArrowDown') {
    event.preventDefault()
    emit('move', 1)
    return
  }
  if (event.key === 'ArrowUp') {
    event.preventDefault()
    emit('move', -1)
    return
  }
  if (event.key === 'Enter') {
    event.preventDefault()
    const action = props.actions[props.activeIndex]
    if (action) emit('select', action)
    return
  }
  if (event.key === 'Escape') {
    event.preventDefault()
    emit('close')
    return
  }
  if (event.key === 'Home') {
    event.preventDefault()
    emit('set-active', 0)
    return
  }
  if (event.key === 'End') {
    event.preventDefault()
    emit('set-active', props.actions.length - 1)
  }
}
</script>

<template>
  <div
    class="grid gap-2"
    role="menu"
    @keydown.stop="onKeydown"
  >
    <button
      v-for="(action, index) in actions"
      :id="`editor-slash-action-${index}`"
      :ref="(element) => setActionButtonRef(element, index)"
      :key="action"
      type="button"
      role="menuitem"
      class="slash-action-btn"
      :class="{ 'slash-action-btn--active': index === activeIndex }"
      :tabindex="index === activeIndex ? 0 : -1"
      @focus="emit('set-active', index)"
      @mouseenter="emit('set-active', index)"
      @click="emit('select', action)"
    >
      {{ t(actionLabels[action]) }}
    </button>
  </div>
</template>

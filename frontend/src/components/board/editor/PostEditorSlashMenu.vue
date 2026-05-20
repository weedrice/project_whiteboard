<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

type SlashAction = 'image' | 'quote' | 'list' | 'link' | 'divider'

const props = defineProps<{
  actions: SlashAction[]
  activeIndex: number
}>()

const emit = defineEmits<{
  (e: 'select', action: SlashAction): void
  (e: 'move', direction: 1 | -1): void
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

const activeItemId = computed(() => `editor-slash-action-${props.activeIndex}`)

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
  }
}
</script>

<template>
  <div
    class="grid gap-2"
    role="menu"
    :aria-activedescendant="activeItemId"
    tabindex="0"
    @keydown.stop="onKeydown"
  >
    <button
      v-for="(action, index) in actions"
      :id="`editor-slash-action-${index}`"
      :key="action"
      type="button"
      role="menuitem"
      class="slash-action-btn"
      :class="{ 'slash-action-btn--active': index === activeIndex }"
      @click="emit('select', action)"
    >
      {{ t(actionLabels[action]) }}
    </button>
  </div>
</template>

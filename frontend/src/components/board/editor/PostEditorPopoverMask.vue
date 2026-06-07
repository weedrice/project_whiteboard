<script setup lang="ts">
defineProps<{
  open: boolean
}>()

const emit = defineEmits<{
  close: []
}>()
</script>

<template>
  <Teleport to="body">
    <div
      v-if="open"
      class="link-popover-mask"
      @click.self="emit('close')"
      @keydown.enter.stop
      @keydown.escape.stop.prevent="emit('close')"
    >
      <slot />
    </div>
  </Teleport>
</template>

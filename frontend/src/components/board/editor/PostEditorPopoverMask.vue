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
      class="link-popover-mask nv-dialog-overlay nv-dialog-overlay--popover"
      @click.self="emit('close')"
      @keydown.enter.stop
    >
      <slot />
    </div>
  </Teleport>
</template>

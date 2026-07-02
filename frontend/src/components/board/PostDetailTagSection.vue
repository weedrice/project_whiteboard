<script setup lang="ts">
import PostTags from '@/components/tag/PostTags.vue'

defineProps<{
  tags: string[]
  boardUrl: string
}>()

const emit = defineEmits<{
  (e: 'tag-click', tag: string): void
}>()
</script>

<template>
  <div
    v-if="tags.length > 0"
    class="nv-post-tags"
  >
    <p class="nv-post-section-label">{{ $t('board.postDetail.tags') }}</p>
    <PostTags
      :modelValue="tags"
      :readOnly="true"
      :boardUrl="boardUrl"
      compact
      @tag-click="emit('tag-click', $event)"
    />
  </div>
</template>

<style scoped>
.nv-post-tags {
  display: grid;
  gap: 0.55rem;
}

.nv-post-section-label {
  color: var(--nv-muted);
  font-family: 'JetBrains Mono', monospace;
  font-size: 0.74rem;
  font-weight: 600;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}
</style>

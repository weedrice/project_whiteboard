<script setup lang="ts">
import BaseButton from '@/components/common/ui/BaseButton.vue'

defineProps<{
  message: string
}>()

const emit = defineEmits<{
  (e: 'back'): void
}>()
</script>

<template>
  <div class="px-4 py-12 text-center text-sm nv-form-error sm:px-6">
    {{ message }}
    <div class="mt-4">
      <BaseButton variant="ghost" size="sm" @click="emit('back')">
        {{ $t('common.back') }}
      </BaseButton>
    </div>
  </div>
</template>

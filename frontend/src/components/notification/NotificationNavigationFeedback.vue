<script setup lang="ts">
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseSpinner from '@/components/common/ui/BaseSpinner.vue'

defineProps<{ pending?: boolean; error?: string }>()
defineEmits<{ retry: [] }>()
</script>

<template>
  <div v-if="pending" class="flex items-center gap-2 px-4 py-3 text-sm nv-text-muted" role="status">
    <BaseSpinner size="sm" />
    {{ $t('notification.navigating') }}
  </div>
  <div v-else-if="error" class="space-y-2 px-4 py-3 text-sm nv-form-error" role="alert">
    <p>{{ error }}</p>
    <BaseButton type="button" variant="secondary" size="sm" @click="$emit('retry')">
      {{ $t('common.error.retry') }}
    </BaseButton>
  </div>
</template>

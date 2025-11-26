<template>
  <BaseButton
    :variant="isBlocked ? 'secondary' : 'danger'"
    size="sm"
    @click="toggleBlock"
    :disabled="loading"
  >
    {{ isBlocked ? 'Unblock' : 'Block' }}
  </BaseButton>
</template>

<script setup>
import { ref } from 'vue'
import BaseButton from '@/components/common/BaseButton.vue'
import axios from '@/api'

const props = defineProps({
  userId: {
    type: [String, Number],
    required: true
  },
  initialBlocked: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['block-change'])

const isBlocked = ref(props.initialBlocked)
const loading = ref(false)

const toggleBlock = async () => {
  if (!confirm(isBlocked.value ? 'Unblock this user?' : 'Block this user?')) return

  loading.value = true
  try {
    if (isBlocked.value) {
      await axios.delete(`/api/users/blocks/${props.userId}`)
      isBlocked.value = false
    } else {
      await axios.post(`/api/users/blocks/${props.userId}`)
      isBlocked.value = true
    }
    emit('block-change', isBlocked.value)
  } catch (error) {
    console.error('Failed to toggle block:', error)
    alert('Failed to process request')
  } finally {
    loading.value = false
  }
}
</script>

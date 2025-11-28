<script setup>
import { ref } from 'vue'
import { commentApi } from '@/api/comment'

const props = defineProps({
  postId: {
    type: [Number, String],
    required: true
  },
  parentId: {
    type: [Number, String],
    default: null
  }
})

const emit = defineEmits(['success', 'cancel'])

const content = ref('')
const isSubmitting = ref(false)

async function handleSubmit() {
  if (!content.value.trim()) return

  isSubmitting.value = true
  try {
    const payload = {
      content: content.value,
      parentId: props.parentId
    }
    const { data } = await commentApi.createComment(props.postId, payload)
    if (data.success) {
      content.value = ''
      emit('success')
    }
  } catch (err) {
    console.error('Failed to post comment:', err)
    alert('Failed to post comment.')
  } finally {
    isSubmitting.value = false
  }
}
</script>

<template>
  <form @submit.prevent="handleSubmit" class="mt-4">
    <div>
      <label for="comment" class="sr-only">Add a comment</label>
      <textarea
        id="comment"
        rows="3"
        v-model="content"
        class="input-base"
        :placeholder="parentId ? 'Write a reply...' : 'Add a comment...'"
        required
      ></textarea>
    </div>
    <div class="mt-3 flex items-center justify-end">
      <button
        v-if="parentId"
        type="button"
        @click="emit('cancel')"
        class="btn-secondary mr-3"
      >
        Cancel
      </button>
      <button
        type="submit"
        :disabled="isSubmitting"
        class="btn-primary disabled:opacity-50"
      >
        {{ isSubmitting ? 'Posting...' : (parentId ? 'Reply' : 'Post Comment') }}
      </button>
    </div>
  </form>
</template>

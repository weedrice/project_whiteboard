<script setup>
import { ref } from 'vue'
import { commentApi } from '@/api/comment'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const props = defineProps({
  postId: {
    type: [Number, String],
    required: true
  },
  parentId: {
    type: [Number, String],
    default: null
  },
  initialContent: {
    type: String,
    default: ''
  },
  commentId: {
    type: [Number, String],
    default: null
  }
})

const emit = defineEmits(['success', 'cancel'])

const content = ref(props.initialContent)
const isSubmitting = ref(false)

async function handleSubmit() {
  if (!content.value.trim()) return

  isSubmitting.value = true
  try {
    if (props.commentId) {
        // Update existing comment
        const { data } = await commentApi.updateComment(props.commentId, { content: content.value })
        if (data.success) {
            emit('success')
        }
    } else {
        // Create new comment
        const payload = {
        content: content.value,
        parentId: props.parentId
        }
        const { data } = await commentApi.createComment(props.postId, payload)
        if (data.success) {
        content.value = ''
        emit('success')
        }
    }
  } catch (err) {
    console.error('Failed to save comment:', err)
    alert(t('comment.saveFailed'))
  } finally {
    isSubmitting.value = false
  }
}
</script>

<template>
  <form @submit.prevent="handleSubmit" class="mt-4">
    <div>
      <label for="comment" class="sr-only">{{ $t('comment.title') }}</label>
      <textarea
        id="comment"
        rows="3"
        v-model="content"
        class="input-base"
        :placeholder="parentId ? $t('comment.writeReply') : $t('comment.writeComment')"
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
        {{ $t('common.cancel') }}
      </button>
      <button
        type="submit"
        :disabled="isSubmitting"
        class="btn-primary disabled:opacity-50"
      >
        {{ isSubmitting ? $t('comment.posting') : (parentId ? $t('comment.reply') : $t('comment.postComment')) }}
      </button>
    </div>
  </form>
</template>

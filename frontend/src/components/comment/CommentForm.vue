<script setup>
import { ref } from 'vue'
import { useComment } from '@/composables/useComment'
import { useI18n } from 'vue-i18n'
import logger from '@/utils/logger'

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

const { useCreateComment, useUpdateComment } = useComment()
const { mutate: createComment, isLoading: isCreating } = useCreateComment()
const { mutate: updateComment, isLoading: isUpdating } = useUpdateComment()

const content = ref(props.initialContent)
const isSubmitting = ref(false) // Keep local state for button disabled, or use isCreating/isUpdating

async function handleSubmit() {
  if (!content.value.trim()) return

  isSubmitting.value = true

  if (props.commentId) {
      // Update existing comment
      updateComment({ commentId: props.commentId, data: { content: content.value } }, {
          onSuccess: () => {
              emit('success')
              isSubmitting.value = false
          },
          onError: (err) => {
              logger.error('Failed to save comment:', err)
              alert(t('comment.saveFailed'))
              isSubmitting.value = false
          }
      })
  } else {
      // Create new comment
      const payload = {
          content: content.value,
          parentId: props.parentId
      }
      createComment({ postId: props.postId, data: payload }, {
          onSuccess: () => {
              content.value = ''
              emit('success')
              isSubmitting.value = false
          },
          onError: (err) => {
              logger.error('Failed to save comment:', err)
              alert(t('comment.saveFailed'))
              isSubmitting.value = false
          }
      })
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

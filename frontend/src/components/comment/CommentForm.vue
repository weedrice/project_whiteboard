<script setup lang="ts">
import { ref } from 'vue'
import { useComment } from '@/composables/useComment'
import { useI18n } from 'vue-i18n'
import logger from '@/utils/logger'
import type { CommentPayload } from '@/api/comment'
import BaseButton from '@/components/common/BaseButton.vue'
import BaseTextarea from '@/components/common/BaseTextarea.vue'
import { useToastStore } from '@/stores/toast'

const toastStore = useToastStore()

const { t } = useI18n()

const props = withDefaults(defineProps<{
  postId: number | string
  parentId?: number | string | null
  initialContent?: string
  commentId?: number | string | null
}>(), {
  parentId: null,
  initialContent: '',
  commentId: null
})

const emit = defineEmits<{
  (e: 'success'): void
  (e: 'cancel'): void
}>()

const { useCreateComment, useUpdateComment } = useComment()
const { mutate: createComment, isPending: isCreating } = useCreateComment()
const { mutate: updateComment, isPending: isUpdating } = useUpdateComment()

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
        toastStore.addToast(t('comment.saveFailed'), 'error')
        isSubmitting.value = false
      }
    })
  } else {
    // Create new comment
    const payload: CommentPayload = {
      content: content.value,
      parentId: props.parentId ? Number(props.parentId) : null
    }
    createComment({ postId: props.postId, data: payload }, {
      onSuccess: () => {
        content.value = ''
        emit('success')
        isSubmitting.value = false
      },
      onError: (err) => {
        logger.error('Failed to save comment:', err)
        toastStore.addToast(t('comment.saveFailed'), 'error')
        isSubmitting.value = false
      }
    })
  }
}
</script>

<template>
  <form @submit.prevent="handleSubmit" class="mt-4">
    <div>
      <BaseTextarea id="comment" v-model="content" rows="3"
        :placeholder="parentId ? $t('comment.writeReply') : $t('comment.writeComment')" required hideLabel />
    </div>
    <div class="mt-3 flex items-center justify-end">
      <BaseButton v-if="parentId" type="button" @click="emit('cancel')" variant="secondary" class="mr-3">
        {{ $t('common.cancel') }}
      </BaseButton>
      <BaseButton type="submit" :loading="isSubmitting" variant="primary">
        {{ isSubmitting ? $t('comment.posting') : (parentId ? $t('comment.reply') : $t('comment.postComment')) }}
      </BaseButton>
    </div>
  </form>
</template>

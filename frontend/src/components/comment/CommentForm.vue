<script setup lang="ts">
import { computed, ref } from 'vue'
import { useComment } from '@/composables/useComment'
import { useI18n } from 'vue-i18n'
import logger from '@/utils/logger'
import type { CommentPayload } from '@/api/comment'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseTextarea from '@/components/common/ui/BaseTextarea.vue'
import { useToastStore } from '@/stores/toast'
import { useAuthStore } from '@/stores/auth'
import EmoticonPicker from '@/components/common/widgets/EmoticonPicker.vue'
import type { EmoticonImage } from '@/types/emoticon'
import { Smile } from 'lucide-vue-next'

const toastStore = useToastStore()
const authStore = useAuthStore()

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
const isSubmitting = computed(() => isCreating.value || isUpdating.value)
const trimmedContent = computed(() => content.value.trim())
const canSubmit = computed(() => !!trimmedContent.value && !isSubmitting.value)
const showEmoticonPicker = ref(false)

// 이모티콘 선택 시 바로 댓글 등록
const handleEmoticonSelect = (image: EmoticonImage) => {
  showEmoticonPicker.value = false
  
  if (isSubmitting.value) return
  
  // 이모티콘 이미지를 마크다운 형식으로 댓글 내용에 설정
  const emoticonContent = `![emoticon](${image.imageUrl})`
  
  const payload: CommentPayload = {
    content: emoticonContent,
    parentId: props.parentId ? Number(props.parentId) : null
  }
  
  createComment({ postId: props.postId, data: payload }, {
    onSuccess: () => {
      emit('success')
    },
    onError: (err) => {
      logger.error('Failed to post emoticon comment:', err)
      toastStore.addToast(t('comment.saveFailed'), 'error')
    }
  })
}

async function handleSubmit() {
  if (!canSubmit.value) return

  if (props.commentId) {
    // Update existing comment
    updateComment({ commentId: props.commentId, data: { content: trimmedContent.value } }, {
      onSuccess: () => {
        emit('success')
      },
      onError: (err) => {
        logger.error('Failed to save comment:', err)
        toastStore.addToast(t('comment.saveFailed'), 'error')
      }
    })
  } else {
    // Create new comment
    const payload: CommentPayload = {
      content: trimmedContent.value,
      parentId: props.parentId ? Number(props.parentId) : null
    }
    createComment({ postId: props.postId, data: payload }, {
      onSuccess: () => {
        content.value = ''
        emit('success')
      },
      onError: (err) => {
        logger.error('Failed to save comment:', err)
        toastStore.addToast(t('comment.saveFailed'), 'error')
      }
    })
  }
}
</script>

<template>
  <form @submit.prevent="handleSubmit" class="mt-3 sm:mt-4 text-sm sm:text-base">
    <div class="relative">
      <BaseTextarea id="comment" v-model="content" rows="3" maxlength="1000"
        :placeholder="parentId ? $t('comment.writeReply') : $t('comment.writeComment')" required hideLabel />
      
      <!-- 이모티콘 피커 -->
      <EmoticonPicker 
        :show="showEmoticonPicker" 
        @select="handleEmoticonSelect"
        @close="showEmoticonPicker = false" 
      />
    </div>
    <div class="mt-3 flex items-center justify-between">
      <!-- 이모티콘 버튼 -->
      <div>
        <button
          v-if="authStore.isAuthenticated && !commentId"
          type="button"
          @click="showEmoticonPicker = !showEmoticonPicker"
          class="inline-flex items-center justify-center px-2 py-1.5 sm:px-3 sm:py-1.5 text-xs sm:text-sm text-gray-600 dark:text-gray-400 hover:text-indigo-600 dark:hover:text-indigo-400 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-lg transition-colors"
          :class="{ 'text-indigo-600 dark:text-indigo-400 bg-gray-100 dark:bg-gray-700': showEmoticonPicker }"
          title="노비콘"
        >
          <Smile class="w-4 h-4 sm:w-5 sm:h-5" />
        </button>
      </div>
      
      <div class="flex items-center">
        <BaseButton v-if="parentId" type="button" @click="emit('cancel')" variant="secondary" size="sm" class="mr-3">
          {{ $t('common.cancel') }}
        </BaseButton>
        <BaseButton type="submit" :loading="isSubmitting" :disabled="!canSubmit" variant="primary" size="sm">
          {{ isSubmitting ? $t('comment.posting') : (parentId ? $t('comment.reply') : $t('common.submit')) }}
        </BaseButton>
      </div>
    </div>
  </form>
</template>

<style scoped>
/* 이모티콘 피커 위치 조정 (데스크톱만: 댓글 입력창 위쪽) / 모바일은 피커 내부 fixed 중앙 유지 */
@media (min-width: 640px) {
  :deep(.emoticon-picker) {
    top: auto;
    bottom: 100%;
    margin-bottom: 8px;
    left: 0;
    right: auto;
  }
}
</style>

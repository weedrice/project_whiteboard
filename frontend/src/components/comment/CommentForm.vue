<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import { useComment } from '@/features/comments/queries/useComment'
import { unwrapAxiosApiData } from '@/api/response'
import type { CommentCreateResponse } from '@/api/comment'
import type { ApiResponse } from '@/types'
import type { AxiosResponse } from 'axios'
import { useI18n } from 'vue-i18n'
import logger from '@/utils/logger'
import type { CommentPayload } from '@/api/comment'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseTextarea from '@/components/common/ui/BaseTextarea.vue'
import MentionSuggestionList from '@/features/mentions/MentionSuggestionList.vue'
import { useMentionAutocomplete } from '@/features/mentions/useMentionAutocomplete'
import { useToastStore } from '@/stores/toast'
import { useAuthStore } from '@/stores/auth'
import EmoticonPicker from '@/components/common/widgets/EmoticonPicker.vue'
import type { EmoticonImage } from '@/types/emoticon'
import type { CommentMention, MentionCandidate } from '@/types'
import { Smile } from 'lucide-vue-next'
import { useFieldValidation } from '@/composables/useFieldValidation'
import { usePwaReloadBlocker } from '@/pwaReloadGuard'

const toastStore = useToastStore()
const authStore = useAuthStore()

const { t } = useI18n()

const props = withDefaults(defineProps<{
  postId: number | string
  parentId?: number | string | null
  initialContent?: string
  initialMentions?: CommentMention[]
  commentId?: number | string | null
}>(), {
  parentId: null,
  initialContent: '',
  initialMentions: () => [],
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
usePwaReloadBlocker(computed(() => content.value !== props.initialContent))
const commentValues = computed(() => ({ content: content.value }))
const commentValidation = useFieldValidation<'content'>({
  validators: {
    content: (values) => String(values.content ?? '').trim() ? '' : t('comment.writeComment'),
  },
})
const showEmoticonPicker = ref(false)
const selectedMentionUsers = ref<MentionCandidate[]>(
  props.initialMentions.map((mention) => ({
    userId: mention.userId,
    displayName: mention.displayName,
    profileImageUrl: mention.profileImageUrl ?? null,
  })),
)
const textareaRoot = ref<InstanceType<typeof BaseTextarea> | null>(null)
const idSegment = (value: number | string) => String(value).replace(/[^a-zA-Z0-9_-]/g, '-')
const textareaId = computed(() => {
  if (props.commentId) {
    return `comment-edit-${idSegment(props.commentId)}`
  }

  if (props.parentId) {
    return `comment-reply-${idSegment(props.parentId)}`
  }

  return `comment-new-${idSegment(props.postId)}`
})
const mentionMenuId = computed(() => `${textareaId.value}-mention-listbox`)
const emoticonButtonLabel = computed(() => t('board.writePost.toolbar.emoticon'))
const mentionedUserIds = computed(() => selectedMentionUsers.value
  .filter((user) => content.value.includes(`@${user.displayName}`))
  .map((user) => user.userId)
  .slice(0, 10))

const getTextarea = () => {
  const root = textareaRoot.value?.$el as HTMLElement | undefined
  return root?.querySelector('textarea') ?? (document.getElementById(textareaId.value) as HTMLTextAreaElement | null)
}
commentValidation.registerFocus('content', () => getTextarea()?.focus())

const findActiveMention = (textarea: HTMLTextAreaElement | null) => {
  if (!textarea || props.commentId) return null

  const caret = textarea.selectionStart ?? content.value.length
  const beforeCaret = content.value.slice(0, caret)
  const match = beforeCaret.match(/(^|\s)@([^\s@]{1,20})$/)
  if (!match) return null

  const query = match[2].trim()
  if (!query) return null

  return {
    query,
    start: beforeCaret.length - query.length - 1,
    end: caret,
  }
}

const insertMention = async (candidate: MentionCandidate, activeMention: { start: number; end: number }) => {
  const textarea = getTextarea()

  const before = content.value.slice(0, activeMention.start)
  const after = content.value.slice(activeMention.end)
  const insertedText = `@${candidate.displayName} `
  content.value = `${before}${insertedText}${after}`

  if (!selectedMentionUsers.value.some((user) => user.userId === candidate.userId)
    && selectedMentionUsers.value.length < 10) {
    selectedMentionUsers.value = [...selectedMentionUsers.value, candidate]
  }

  await nextTick()
  textarea?.focus()
  const nextCaret = before.length + insertedText.length
  textarea?.setSelectionRange(nextCaret, nextCaret)
}

const mentionAutocomplete = useMentionAutocomplete({
  resolveRange: () => findActiveMention(getTextarea()),
  onSelect: insertMention,
})

const mentionCandidates = mentionAutocomplete.items
const mentionMenuOpen = mentionAutocomplete.isOpen
const selectedMentionIndex = mentionAutocomplete.selectedIndex
const mentionLoading = mentionAutocomplete.isLoading
const mentionError = mentionAutocomplete.error
const activeMentionOptionId = computed(() => {
  const candidate = mentionCandidates.value[selectedMentionIndex.value]
  return mentionMenuOpen.value && candidate
    ? `${mentionMenuId.value}-option-${candidate.userId}`
    : undefined
})
const updateMentionCandidates = mentionAutocomplete.refresh
const closeMentionMenu = mentionAutocomplete.close
const selectMention = mentionAutocomplete.select
const handleMentionKeydown = mentionAutocomplete.handleKeydown

const shouldShowLocalErrorToast = (error: unknown) => {
  return !(error && typeof error === 'object' && (error as { suppressGlobalErrorToast?: boolean }).suppressGlobalErrorToast)
}

const handleCommentSubmitError = (message: string, error: unknown) => {
  logger.error(message, error)
  if (shouldShowLocalErrorToast(error)) {
    toastStore.addToast(t('comment.saveFailed'), 'error')
  }
}

const showEarnedPointsToast = (response: AxiosResponse<ApiResponse<CommentCreateResponse>>) => {
  const earnedPoints = unwrapAxiosApiData(response).earnedPoints
  if (typeof earnedPoints === 'number' && earnedPoints > 0) {
    toastStore.addToast(t('common.pointEarned', { points: earnedPoints }), 'success')
  }
}

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
    onSuccess: (response) => {
      showEarnedPointsToast(response)
      emit('success')
    },
    onError: (err) => {
      handleCommentSubmitError('Failed to post emoticon comment:', err)
    }
  })
}

async function handleSubmit() {
  if (!await commentValidation.validateAll(commentValues.value)) return
  if (!canSubmit.value) return

  if (props.commentId) {
    // Update existing comment
    const payload: CommentPayload = { content: trimmedContent.value }
    if (props.initialMentions.length > 0 || mentionedUserIds.value.length > 0) {
      payload.mentionedUserIds = mentionedUserIds.value
    }
    updateComment({ commentId: props.commentId, postId: props.postId, data: payload }, {
      onSuccess: () => {
        emit('success')
      },
      onError: (err) => {
        handleCommentSubmitError('Failed to save comment:', err)
      }
    })
  } else {
    // Create new comment
    const payload: CommentPayload = {
      content: trimmedContent.value,
      parentId: props.parentId ? Number(props.parentId) : null
    }
    if (mentionedUserIds.value.length > 0) {
      payload.mentionedUserIds = mentionedUserIds.value
    }
    createComment({ postId: props.postId, data: payload }, {
      onSuccess: (response) => {
        showEarnedPointsToast(response)
        content.value = ''
        selectedMentionUsers.value = []
        closeMentionMenu()
        emit('success')
      },
      onError: (err) => {
        handleCommentSubmitError('Failed to save comment:', err)
      }
    })
  }
}
</script>

<template>
  <form @submit.prevent="handleSubmit" class="mt-3 sm:mt-4 text-sm sm:text-base">
    <div class="relative">
      <BaseTextarea ref="textareaRoot" :id="textareaId" v-model="content" rows="3" maxlength="1000" name="comment-content"
        :label="parentId ? $t('comment.writeReply') : $t('comment.writeComment')"
        :placeholder="parentId ? $t('comment.writeReply') : $t('comment.writeComment')" required hideLabel
        :error="commentValidation.visibleError('content')"
        role="combobox"
        aria-autocomplete="list"
        aria-haspopup="listbox"
        :aria-expanded="mentionMenuOpen || mentionLoading || !!mentionError"
        :aria-controls="mentionMenuId"
        :aria-activedescendant="activeMentionOptionId"
        @blur="commentValidation.touchField('content', commentValues)"
        @keyup="updateMentionCandidates"
        @click="updateMentionCandidates"
        @keydown="handleMentionKeydown" />

      <div
        v-if="mentionMenuOpen || mentionLoading || mentionError"
        class="comment-mention-suggestion-popover"
      >
        <MentionSuggestionList
          :id="mentionMenuId"
          :items="mentionCandidates"
          :selected-index="selectedMentionIndex"
          :loading="mentionLoading"
          :error="!!mentionError"
          @select="selectMention"
          @retry="updateMentionCandidates"
        />
      </div>
      
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
          class="inline-flex items-center justify-center px-2 py-1.5 sm:px-3 sm:py-1.5 text-xs sm:text-sm nv-text-muted nv-hover-surface hover:text-[var(--nv-accent)] rounded-lg transition-colors"
          :class="{ 'text-[var(--nv-accent)] nv-active-surface': showEmoticonPicker }"
          :aria-label="emoticonButtonLabel"
          :aria-pressed="showEmoticonPicker"
          :title="emoticonButtonLabel"
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
.comment-mention-suggestion-popover {
  position: absolute;
  left: 0.5rem;
  right: 0.5rem;
  bottom: calc(100% + 0.25rem);
  z-index: 20;
}

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

<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import { useComment } from '@/composables/useComment'
import { useI18n } from 'vue-i18n'
import logger from '@/utils/logger'
import type { CommentPayload } from '@/api/comment'
import { userAccountApi } from '@/api/userAccountApi'
import { unwrapAxiosApiData } from '@/api/response'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseTextarea from '@/components/common/ui/BaseTextarea.vue'
import { useToastStore } from '@/stores/toast'
import { useAuthStore } from '@/stores/auth'
import EmoticonPicker from '@/components/common/widgets/EmoticonPicker.vue'
import type { EmoticonImage } from '@/types/emoticon'
import type { CommentMention, MentionCandidate } from '@/types'
import { Smile } from 'lucide-vue-next'

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
const showEmoticonPicker = ref(false)
const mentionCandidates = ref<MentionCandidate[]>([])
const mentionMenuOpen = ref(false)
const selectedMentionIndex = ref(0)
const selectedMentionUsers = ref<MentionCandidate[]>(
  props.initialMentions.map((mention) => ({
    userId: mention.userId,
    displayName: mention.displayName,
    profileImageUrl: mention.profileImageUrl ?? null,
  })),
)
const textareaRoot = ref<InstanceType<typeof BaseTextarea> | null>(null)
let mentionLookupSeq = 0
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
const emoticonButtonLabel = computed(() => t('board.writePost.toolbar.emoticon'))
const mentionedUserIds = computed(() => selectedMentionUsers.value
  .filter((user) => content.value.includes(`@${user.displayName}`))
  .map((user) => user.userId)
  .slice(0, 10))

const getTextarea = () => {
  const root = textareaRoot.value?.$el as HTMLElement | undefined
  return root?.querySelector('textarea') ?? (document.getElementById(textareaId.value) as HTMLTextAreaElement | null)
}

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

const closeMentionMenu = () => {
  mentionMenuOpen.value = false
  mentionCandidates.value = []
  selectedMentionIndex.value = 0
}

const updateMentionCandidates = async () => {
  const activeMention = findActiveMention(getTextarea())
  if (!activeMention) {
    closeMentionMenu()
    return
  }

  const seq = ++mentionLookupSeq
  try {
    const response = await userAccountApi.getMentionCandidates(activeMention.query)
    if (seq !== mentionLookupSeq) return

    mentionCandidates.value = unwrapAxiosApiData(response)
    mentionMenuOpen.value = mentionCandidates.value.length > 0
    selectedMentionIndex.value = 0
  } catch (error) {
    logger.error('Failed to load mention candidates:', error)
    if (seq === mentionLookupSeq) {
      closeMentionMenu()
    }
  }
}

const selectMention = async (candidate: MentionCandidate) => {
  const textarea = getTextarea()
  const activeMention = findActiveMention(textarea)
  if (!activeMention) {
    closeMentionMenu()
    return
  }

  const before = content.value.slice(0, activeMention.start)
  const after = content.value.slice(activeMention.end)
  const insertedText = `@${candidate.displayName} `
  content.value = `${before}${insertedText}${after}`

  if (!selectedMentionUsers.value.some((user) => user.userId === candidate.userId)
    && selectedMentionUsers.value.length < 10) {
    selectedMentionUsers.value = [...selectedMentionUsers.value, candidate]
  }

  closeMentionMenu()
  await nextTick()
  textarea?.focus()
  const nextCaret = before.length + insertedText.length
  textarea?.setSelectionRange(nextCaret, nextCaret)
}

const handleMentionKeydown = (event: KeyboardEvent) => {
  if (!mentionMenuOpen.value || mentionCandidates.value.length === 0) return

  if (event.key === 'ArrowDown') {
    event.preventDefault()
    selectedMentionIndex.value = (selectedMentionIndex.value + 1) % mentionCandidates.value.length
    return
  }

  if (event.key === 'ArrowUp') {
    event.preventDefault()
    selectedMentionIndex.value = (selectedMentionIndex.value - 1 + mentionCandidates.value.length)
      % mentionCandidates.value.length
    return
  }

  if (event.key === 'Enter' || event.key === 'Tab') {
    event.preventDefault()
    const candidate = mentionCandidates.value[selectedMentionIndex.value]
    if (candidate) {
      void selectMention(candidate)
    }
    return
  }

  if (event.key === 'Escape') {
    event.preventDefault()
    closeMentionMenu()
  }
}

const shouldShowLocalErrorToast = (error: unknown) => {
  return !(error && typeof error === 'object' && (error as { suppressGlobalErrorToast?: boolean }).suppressGlobalErrorToast)
}

const handleCommentSubmitError = (message: string, error: unknown) => {
  logger.error(message, error)
  if (shouldShowLocalErrorToast(error)) {
    toastStore.addToast(t('comment.saveFailed'), 'error')
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
    onSuccess: () => {
      emit('success')
    },
    onError: (err) => {
      handleCommentSubmitError('Failed to post emoticon comment:', err)
    }
  })
}

async function handleSubmit() {
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
      onSuccess: () => {
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
        @keyup="updateMentionCandidates"
        @click="updateMentionCandidates"
        @keydown="handleMentionKeydown" />

      <div
        v-if="mentionMenuOpen"
        class="mention-suggestion-menu"
        role="listbox"
      >
        <button
          v-for="(candidate, index) in mentionCandidates"
          :key="candidate.userId"
          type="button"
          class="mention-suggestion-item"
          :data-active="index === selectedMentionIndex"
          role="option"
          :aria-selected="index === selectedMentionIndex"
          @mousedown.prevent="selectMention(candidate)"
        >
          <img
            v-if="candidate.profileImageUrl"
            :src="candidate.profileImageUrl"
            alt=""
            class="mention-suggestion-avatar"
          />
          <span v-else class="mention-suggestion-avatar mention-suggestion-avatar--empty">
            {{ candidate.displayName.slice(0, 1) }}
          </span>
          <span>{{ candidate.displayName }}</span>
        </button>
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
.mention-suggestion-menu {
  position: absolute;
  left: 0.5rem;
  right: 0.5rem;
  bottom: calc(100% + 0.25rem);
  z-index: 20;
  max-height: 14rem;
  overflow-y: auto;
  border: 1px solid var(--nv-border);
  border-radius: 0.5rem;
  background: var(--nv-surface);
  box-shadow: var(--nv-shadow-lg);
  padding: 0.25rem;
}

.mention-suggestion-item {
  display: flex;
  width: 100%;
  align-items: center;
  gap: 0.5rem;
  border-radius: 0.375rem;
  padding: 0.45rem 0.5rem;
  color: var(--nv-text);
  text-align: left;
  font-size: 0.875rem;
  transition: background-color 0.15s ease, color 0.15s ease;
}

.mention-suggestion-item:hover,
.mention-suggestion-item[data-active="true"] {
  background: var(--nv-surface-hover);
  color: var(--nv-accent);
}

.mention-suggestion-avatar {
  width: 1.5rem;
  height: 1.5rem;
  flex: 0 0 auto;
  border-radius: 9999px;
  object-fit: cover;
}

.mention-suggestion-avatar--empty {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: var(--nv-surface-hover);
  color: var(--nv-text-muted);
  font-size: 0.75rem;
  font-weight: 700;
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

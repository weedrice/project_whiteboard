<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import { usePost } from '@/features/board/posts/queries/usePost'
import type { PostPoll } from '@/types'

const props = defineProps<{
  postId: string | number
  poll: PostPoll
  isAuthenticated: boolean
}>()

const { t } = useI18n()
const { useVotePoll, useDeletePollVote } = usePost()
const voteMutation = useVotePoll()
const deleteVoteMutation = useDeletePollVote()
const selectedOptionIds = ref<number[]>([])

const totalVotes = computed(() => props.poll.options.reduce((sum, option) => sum + option.voteCount, 0))
const selectedCount = computed(() => props.poll.options.filter((option) => option.selected).length)
const hasVoted = computed(() => selectedCount.value > 0)
const isClosed = computed(() => {
  if (!props.poll.closesAt) return false
  return new Date(props.poll.closesAt).getTime() <= Date.now()
})
const canSubmit = computed(() =>
  props.isAuthenticated
  && !isClosed.value
  && selectedOptionIds.value.length > 0
  && !voteMutation.isPending.value
)

watch(
  () => props.poll,
  (poll) => {
    selectedOptionIds.value = poll.options
      .filter((option) => option.selected)
      .map((option) => option.optionId)
  },
  { immediate: true },
)

function optionPercent(voteCount: number) {
  if (totalVotes.value === 0) return 0
  return Math.round((voteCount / totalVotes.value) * 100)
}

function toggleOption(optionId: number, checked: boolean) {
  if (props.poll.multipleChoiceEnabled) {
    selectedOptionIds.value = checked
      ? [...new Set([...selectedOptionIds.value, optionId])]
      : selectedOptionIds.value.filter((id) => id !== optionId)
    return
  }
  selectedOptionIds.value = checked ? [optionId] : []
}

async function submitVote() {
  if (!canSubmit.value) return
  await voteMutation.mutateAsync({
    postId: props.postId,
    data: { optionIds: selectedOptionIds.value },
  })
}

async function cancelVote() {
  if (!hasVoted.value || deleteVoteMutation.isPending.value) return
  await deleteVoteMutation.mutateAsync(props.postId)
}
</script>

<template>
  <section class="rounded-[var(--nv-radius-md)] border nv-border nv-surface p-4" aria-labelledby="post-poll-title">
    <div class="flex flex-wrap items-start justify-between gap-3">
      <div>
        <p class="nv-kicker">{{ t('board.postDetail.poll.title') }}</p>
        <h2 id="post-poll-title" class="mt-1 text-base font-semibold nv-title">{{ poll.question }}</h2>
      </div>
      <div class="flex flex-wrap gap-2 text-xs nv-text-subtle">
        <span v-if="poll.multipleChoiceEnabled" class="rounded-full border nv-border px-2 py-1">
          {{ t('board.postDetail.poll.multiple') }}
        </span>
        <span v-if="poll.anonymousEnabled" class="rounded-full border nv-border px-2 py-1">
          {{ t('board.postDetail.poll.anonymous') }}
        </span>
        <span v-if="isClosed" class="rounded-full border nv-border px-2 py-1">
          {{ t('board.postDetail.poll.closed') }}
        </span>
      </div>
    </div>

    <fieldset class="mt-4 space-y-3">
      <legend class="sr-only">{{ poll.question }}</legend>
      <label
        v-for="option in poll.options"
        :key="option.optionId"
        class="block min-h-11 rounded-md border nv-border p-3"
      >
        <div class="flex items-center gap-3">
          <input
            :type="poll.multipleChoiceEnabled ? 'checkbox' : 'radio'"
            name="post-poll-option"
            :checked="selectedOptionIds.includes(option.optionId)"
            :disabled="!isAuthenticated || isClosed"
            class="h-4 w-4"
            @change="toggleOption(option.optionId, ($event.target as HTMLInputElement).checked)"
          >
          <span class="min-w-0 flex-1 text-sm nv-title">{{ option.optionText }}</span>
          <span class="text-sm nv-text-subtle">
            {{ t('board.postDetail.poll.voteCount', { count: option.voteCount }) }}
          </span>
        </div>
        <div class="mt-2 h-2 overflow-hidden rounded-full bg-[var(--nv-elevated)]">
          <div
            class="h-full bg-[var(--nv-accent)]"
            :style="{ width: `${optionPercent(option.voteCount)}%` }"
          />
        </div>
      </label>
    </fieldset>

    <p v-if="!isAuthenticated" class="mt-3 text-sm nv-text-subtle">
      {{ t('board.postDetail.poll.loginRequired') }}
    </p>
    <p v-else-if="totalVotes === 0" class="mt-3 text-sm nv-text-subtle">
      {{ t('board.postDetail.poll.noVotes') }}
    </p>

    <div v-if="isAuthenticated && !isClosed" class="mt-4 flex flex-wrap justify-end gap-2">
      <BaseButton
        v-if="hasVoted"
        type="button"
        variant="secondary"
        size="sm"
        :loading="deleteVoteMutation.isPending.value"
        @click="cancelVote"
      >
        {{ t('board.postDetail.poll.cancelVote') }}
      </BaseButton>
      <BaseButton
        type="button"
        size="sm"
        :disabled="!canSubmit"
        :loading="voteMutation.isPending.value"
        @click="submitVote"
      >
        {{ hasVoted ? t('board.postDetail.poll.revote') : t('board.postDetail.poll.vote') }}
      </BaseButton>
    </div>
  </section>
</template>

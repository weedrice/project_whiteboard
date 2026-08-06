<template>
    <PaginatedListCard
        :title="$t('user.message.boxTitle')"
        :icon="Mail"
        :items-count="messages.length"
        :loading="loading"
        :error="error"
        :empty-title="$t('user.message.empty')"
        :page="page"
        :size="size"
        :total-pages="totalPages"
        title-tag="h1"
        actions-visibility="always"
        loading-preset="message-list"
        @retry="fetchMessages"
        @page-change="handlePageChange"
        @size-change="handleSizeChange"
    >
        <template #header-actions>
            <BaseButton v-if="selectedMessages.length > 0" @click="deleteSelectedMessages" variant="danger"
                size="sm" class="min-h-[36px] sm:min-h-0 text-xs sm:text-sm">
                {{ $t('common.delete') }} ({{ selectedMessages.length }})
            </BaseButton>
        </template>

        <template #subheader>
            <BaseSegmentedControl
                :model-value="viewType"
                :options="messageBoxOptions"
                :label="$t('user.message.boxTitle')"
                @update:model-value="changeViewType($event as MailboxViewType)"
            />
        </template>

        <ul class="divide-y divide-[var(--nv-line)]">
            <li v-for="msg in messages" :key="msg.id"
                class="p-3 sm:p-4 nv-hover-surface flex items-stretch transition-colors duration-200 min-h-[52px]">
                <div class="flex items-center justify-center h-full mr-3 sm:mr-4 p-2 -ml-1 cursor-pointer flex-shrink-0" @click.stop>
                    <BaseCheckbox
                        :id="`message-${msg.id}-select`"
                        :value="msg.id"
                        v-model="selectedMessages"
                        :label="$t('user.message.selectMessage', { name: msg.partnerName })"
                        label-class="sr-only"
                    />
                </div>
                <button
                    type="button"
                    class="flex-1 min-w-0 text-left cursor-pointer nv-focus-ring rounded-md"
                    :aria-label="$t('user.message.openMessage', { name: msg.partnerName })"
                    @click="openMessage(msg)"
                >
                    <div class="flex justify-between items-baseline gap-2">
                        <div class="text-sm font-medium nv-accent-text truncate">
                            {{ msg.partnerName }}
                        </div>
                        <div class="text-xs nv-text-subtle flex-shrink-0">
                            {{ formatDate(msg.createdAt) }}
                        </div>
                    </div>
                    <p class="mt-0.5 text-xs sm:text-sm nv-text line-clamp-2"
                        :class="{ 'font-bold': viewType === 'received' && msg.isUnread }">
                        {{ msg.body }}
                    </p>
                </button>
            </li>
        </ul>
    </PaginatedListCard>

    <div v-if="conversationLoading && !selectedMessage" class="py-4 text-center text-sm nv-text-subtle" role="status" aria-live="polite">
        {{ $t('common.loading') }}
    </div>
    <ErrorState
        v-else-if="conversationError && !selectedMessage"
        :message="conversationError"
        show-retry
        @retry="retryConversation"
    />

    <BaseModal :isOpen="!!selectedMessage" :title="conversationTitle" @close="closeConversationAndSyncRoute"
        body-class="overflow-hidden" mobile-full mobile-fit-content size="xl">
        <div
            v-if="selectedMessage"
            data-testid="conversation-modal-content"
            class="flex max-h-[calc(100dvh-10rem)] min-h-0 flex-col gap-4 overflow-hidden sm:max-h-[42rem]"
        >
            <section
                class="flex min-h-0 flex-1 flex-col overflow-hidden rounded-xl border nv-border nv-surface-muted"
                :aria-label="$t('user.message.conversationContext')"
                :aria-busy="conversationLoading"
            >
                <header class="flex shrink-0 items-center justify-between gap-3 border-b nv-border px-4 py-3">
                    <div class="min-w-0">
                        <span class="block text-xs nv-text-subtle">{{ selectedPartnerLabel }}</span>
                        <span class="block truncate text-sm font-semibold nv-title">
                            {{ selectedMessage.partnerName }}
                        </span>
                    </div>
                </header>
                <div class="flex min-h-0 flex-1 flex-col p-3 sm:p-4">
                    <p
                        v-if="pendingConversationMessageCount > 0"
                        data-testid="pending-conversation-messages"
                        class="mb-3 shrink-0 rounded-lg border border-[var(--nv-accent)] px-3 py-2 text-center text-xs nv-accent-bg nv-accent-text"
                        role="status"
                    >
                        {{ $t('user.message.newMessagesPending', { count: pendingConversationMessageCount }) }}
                    </p>
                    <p v-if="conversationLoading" class="py-6 text-center text-sm nv-text-subtle" role="status">
                        {{ $t('common.loading') }}
                    </p>
                    <ErrorState
                        v-else-if="conversationError"
                        :message="conversationError"
                        show-retry
                        @retry="retryConversation"
                    />
                    <div
                        v-else
                        ref="conversationTimelineRef"
                        data-testid="conversation-timeline"
                        class="min-h-[10rem] flex-1 space-y-3 overflow-y-auto pr-1"
                        :aria-busy="conversationLoadingMore"
                        @scroll.passive="handleConversationScroll"
                    >
                        <div v-if="conversationHasMore || conversationOlderError" class="text-center">
                            <p
                                v-if="conversationLoadingMore"
                                class="py-2 text-xs nv-text-subtle"
                                role="status"
                            >
                                {{ $t('common.loading') }}
                            </p>
                            <BaseButton
                                v-else-if="conversationOlderError"
                                data-testid="load-older-conversation"
                                type="button"
                                size="sm"
                                variant="secondary"
                                @click="loadOlderWithScrollPreservation"
                            >
                                {{ $t('user.message.loadOlder') }}
                            </BaseButton>
                            <p
                                v-if="conversationOlderError"
                                class="mt-1 text-xs text-[var(--nv-danger-text)]"
                                role="alert"
                            >
                                {{ conversationOlderError }}
                            </p>
                            <p v-else-if="!conversationLoadingMore" class="py-2 text-xs nv-text-subtle">
                                {{ $t('user.message.scrollForOlder') }}
                            </p>
                        </div>
                        <article
                            v-for="message in conversationMessages"
                            :key="message.id"
                            class="flex"
                            :class="message.sentByMe ? 'justify-end' : 'justify-start'"
                            :data-message-direction="message.sentByMe ? 'sent' : 'received'"
                        >
                            <div
                                class="max-w-[82%] rounded-xl border px-3 py-2.5 text-sm"
                                :class="message.sentByMe
                                    ? 'border-[var(--nv-accent)] nv-accent-bg text-right'
                                    : 'nv-border nv-surface text-left'"
                            >
                                <div class="flex items-center justify-between gap-2" :class="message.sentByMe ? 'flex-row-reverse' : ''">
                                    <span class="truncate text-xs font-medium nv-title">
                                        {{ message.sentByMe ? $t('user.message.me') : message.partnerName }}
                                    </span>
                                    <span class="shrink-0 text-xs nv-text-subtle">{{ formatDate(message.createdAt) }}</span>
                                </div>
                                <p class="mt-1 whitespace-pre-wrap nv-text">{{ message.body }}</p>
                            </div>
                        </article>
                        <p v-if="conversationMessages.length <= 1 && !conversationHasMore" class="text-sm nv-text-subtle">
                            {{ $t('user.message.contextEmpty') }}
                        </p>
                    </div>
                    <form
                        v-if="viewType !== 'sent'"
                        class="mt-4 shrink-0 border-t nv-border pt-4"
                        @submit.prevent="sendReply"
                    >
                        <BaseTextarea
                            v-model="replyContent"
                            :disabled="isSending"
                            :label="$t('user.message.replyTitle')"
                            :maxlength="MESSAGE_CONTENT_MAX_LENGTH"
                            :error="replyContentError"
                            rows="3"
                            class="min-h-[96px]"
                        />
                        <p class="mt-1 text-right text-xs nv-text-muted">
                            {{ $t('user.message.contentLength', {
                                current: replyContent.length,
                                max: MESSAGE_CONTENT_MAX_LENGTH,
                            }) }}
                        </p>
                        <div class="mt-2 flex justify-end gap-2">
                            <BaseButton
                                type="submit"
                                size="sm"
                                :disabled="isSending"
                            >
                                {{ isSending ? $t('common.messages.sending') : $t('common.send') }}
                            </BaseButton>
                        </div>
                    </form>
                </div>
            </section>
        </div>
    </BaseModal>
</template>

<script setup lang="ts">
import BaseModal from '@/components/common/ui/BaseModal.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseCheckbox from '@/components/common/ui/BaseCheckbox.vue'
import BaseTextarea from '@/components/common/ui/BaseTextarea.vue'
import BaseSegmentedControl from '@/components/common/ui/BaseSegmentedControl.vue'
import PaginatedListCard from '@/components/common/ui/PaginatedListCard.vue'
import ErrorState from '@/components/common/ui/ErrorState.vue'
import { Mail } from 'lucide-vue-next'
import { useMailboxResource } from '@/features/user/messages/useMailboxResource'
import type { MailboxViewType } from '@/features/user/messages/useMailboxListState'
import type { MailboxMessageViewModel } from '@/types'
import { formatDate } from '@/utils/date'
import { computed, nextTick, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { usePwaReloadBlocker } from '@/pwaReloadGuard'
import { isMessageContentTooLong, MESSAGE_CONTENT_MAX_LENGTH } from '@/utils/messageValidation'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const conversationTimelineRef = ref<HTMLElement | null>(null)
const OLDER_MESSAGE_SCROLL_THRESHOLD = 64
let latestScrolledPartnerId: number | null = null
const messageBoxOptions = computed(() => [
    { value: 'conversations', label: t('user.message.conversations') },
    { value: 'received', label: t('user.message.received') },
    { value: 'sent', label: t('user.message.sent') },
])

const {
    viewType,
    messages,
    loading,
    error,
    selectedMessage,
    selectedConversationMessages,
    conversationLoading,
    conversationError,
    conversationHasMore,
    conversationLoadingMore,
    conversationOlderError,
    pendingConversationMessageCount,
    selectedMessages,
    page,
    size,
    totalPages,
    replyContent,
    isSending,
    fetchMessages,
    handlePageChange,
    handleSizeChange,
    changeViewType,
    openMessage,
    openConversationByPartnerId,
    closeConversation,
    retryConversation,
    loadOlderConversationMessages,
    deleteSelectedMessages,
    startReply,
    sendReply,
} = useMailboxResource()

function removePartnerIdFromRoute() {
    if (route.query.partnerId === undefined) return
    const { partnerId: _partnerId, ...query } = route.query
    void router.replace({ query })
}

function closeConversationAndSyncRoute() {
    if (isSending.value) return
    closeConversation()
    removePartnerIdFromRoute()
}

async function loadOlderWithScrollPreservation() {
    const timeline = conversationTimelineRef.value
    if (!timeline || conversationLoadingMore.value || !conversationHasMore.value) return

    const previousScrollHeight = timeline.scrollHeight
    const previousScrollTop = timeline.scrollTop
    await loadOlderConversationMessages()
    await nextTick()

    if (conversationTimelineRef.value !== timeline) return
    timeline.scrollTop = previousScrollTop + (timeline.scrollHeight - previousScrollHeight)
}

function handleConversationScroll() {
    const timeline = conversationTimelineRef.value
    if (
        !timeline
        || timeline.scrollTop > OLDER_MESSAGE_SCROLL_THRESHOLD
        || conversationLoadingMore.value
        || !conversationHasMore.value
    ) return

    void loadOlderWithScrollPreservation()
}

const replyContentError = computed(() => isMessageContentTooLong(replyContent.value)
    ? t('user.message.contentTooLong', { max: MESSAGE_CONTENT_MAX_LENGTH })
    : '')
usePwaReloadBlocker(computed(() => replyContent.value.trim().length > 0))

const conversationTitle = computed(() => selectedMessage.value
    ? t('user.message.conversationTitle', { name: selectedMessage.value.partnerName })
    : t('user.message.conversationContext'))

const selectedPartnerLabel = computed(() => {
    if (viewType.value === 'received') return t('user.message.from')
    if (viewType.value === 'sent') return t('user.message.to')
    return t('user.message.conversation')
})

const conversationMessages = computed<MailboxMessageViewModel[]>(() => {
    if (!selectedMessage.value) return []

    const selected = selectedMessage.value
    const byId = new Map<number, MailboxMessageViewModel>()
    byId.set(selected.id, selected)

    const sourceMessages = selectedConversationMessages.value.length > 0
        ? selectedConversationMessages.value
        : messages.value

    sourceMessages
        .filter((message) => message.partnerUserId === selected.partnerUserId)
        .forEach((message) => byId.set(message.id, message))

    return Array.from(byId.values())
        .sort((a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime())
})

watch(
    () => route.query.partnerId,
    (partnerId) => {
        const normalizedPartnerId = Array.isArray(partnerId) ? partnerId[0] : partnerId
        const numericPartnerId = Number(normalizedPartnerId)
        if (!Number.isFinite(numericPartnerId) || numericPartnerId <= 0) {
            closeConversation()
            removePartnerIdFromRoute()
            return
        }
        openConversationByPartnerId(numericPartnerId)
    },
    { immediate: true }
)

watch(
    () => [
        selectedMessage.value?.partnerUserId ?? null,
        conversationLoading.value,
    ] as const,
    async ([partnerId, isLoading]) => {
        if (partnerId == null) {
            latestScrolledPartnerId = null
            return
        }
        if (isLoading || latestScrolledPartnerId === partnerId) return

        await nextTick()
        const timeline = conversationTimelineRef.value
        if (!timeline || selectedMessage.value?.partnerUserId !== partnerId) return
        timeline.scrollTop = timeline.scrollHeight
        latestScrolledPartnerId = partnerId
    },
    { flush: 'post' },
)

defineExpose({
    startReply,
    replyContent,
    sendReply,
})
</script>

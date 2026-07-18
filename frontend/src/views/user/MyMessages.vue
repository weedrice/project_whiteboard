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

    <BaseModal :isOpen="!!selectedMessage" :title="$t('user.message.detailTitle')" @close="closeConversationAndSyncRoute"
        mobile-full mobile-fit-content size="2xl">
        <div v-if="selectedMessage" data-testid="message-detail-content" class="space-y-4">
            <div class="grid gap-5 lg:grid-cols-[minmax(0,1fr)_minmax(16rem,20rem)]">
                <section class="min-w-0" :aria-busy="messageDetailLoading">
                    <p v-if="messageDetailLoading" class="py-8 text-center text-sm nv-text-subtle" role="status">
                        {{ $t('common.loading') }}
                    </p>
                    <ErrorState
                        v-else-if="messageDetailError"
                        :message="messageDetailError"
                        show-retry
                        @retry="retryMessageDetail"
                    />
                    <template v-else>
                    <div class="flex flex-col sm:flex-row sm:justify-between sm:items-start gap-1 border-b nv-border pb-3">
                        <div class="min-w-0">
                            <span class="block text-xs nv-text-subtle">{{ selectedPartnerLabel }}</span>
                            <span class="text-sm font-medium nv-title truncate block">{{
                                selectedMessage.partnerName }}</span>
                        </div>
                        <span class="text-xs nv-text-subtle flex-shrink-0">{{ formatDate(selectedMessage.createdAt) }}</span>
                    </div>
                    <div class="text-sm nv-text whitespace-pre-wrap min-h-[120px] overflow-y-auto max-h-[50vh] sm:max-h-none pt-4">
                        {{ selectedMessage.body }}
                    </div>
                    </template>
                </section>

                <section class="rounded-lg border nv-border nv-surface-muted p-3" :aria-busy="conversationLoading">
                    <h3 class="text-sm font-semibold nv-title">{{ $t('user.message.conversationContext') }}</h3>
                    <p v-if="conversationLoading" class="py-6 text-center text-sm nv-text-subtle" role="status">
                        {{ $t('common.loading') }}
                    </p>
                    <ErrorState
                        v-else-if="conversationError"
                        :message="conversationError"
                        show-retry
                        @retry="retryConversation"
                    />
                    <div v-else class="mt-3 max-h-[18rem] space-y-2 overflow-y-auto pr-1">
                        <article
                            v-for="message in conversationMessages"
                            :key="message.id"
                            class="flex"
                            :class="message.sentByMe ? 'justify-end' : 'justify-start'"
                            :data-message-direction="message.sentByMe ? 'sent' : 'received'"
                        >
                            <div
                                class="max-w-[85%] rounded-lg border p-3 text-sm"
                                :class="[
                                    message.sentByMe
                                        ? 'border-[var(--nv-accent)] nv-accent-bg text-right'
                                        : 'nv-border nv-surface text-left',
                                    message.isCurrent ? 'ring-1 ring-[var(--nv-accent)]' : ''
                                ]"
                            >
                                <div class="flex items-center justify-between gap-2" :class="message.sentByMe ? 'flex-row-reverse' : ''">
                                    <span class="truncate text-xs font-medium nv-title">
                                        {{ message.sentByMe ? $t('user.message.me') : message.partnerName }}
                                        <span v-if="message.isCurrent" class="nv-text-subtle">
                                            · {{ $t('user.message.currentMessage') }}
                                        </span>
                                    </span>
                                    <span class="shrink-0 text-xs nv-text-subtle">{{ formatDate(message.createdAt) }}</span>
                                </div>
                                <p class="mt-1 whitespace-pre-wrap nv-text-subtle">{{ message.body }}</p>
                            </div>
                        </article>
                        <p v-if="conversationMessages.length <= 1" class="text-sm nv-text-subtle">
                            {{ $t('user.message.contextEmpty') }}
                        </p>
                    </div>
                    <form
                        v-if="viewType !== 'sent'"
                        class="mt-4 border-t nv-border pt-3"
                        @submit.prevent="sendReply"
                    >
                        <BaseTextarea
                            v-model="replyContent"
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
                            <BaseButton
                                type="button"
                                size="sm"
                                variant="secondary"
                                @click="cancelInlineReply"
                            >
                                {{ $t('common.cancel') }}
                            </BaseButton>
                        </div>
                    </form>
                </section>
            </div>

            <div class="flex flex-col-reverse sm:flex-row justify-end gap-2 sm:space-x-2 pt-4 border-t nv-border">
                <BaseButton @click="closeConversationAndSyncRoute" variant="secondary"
                    class="w-full sm:w-auto min-h-[44px] order-1 sm:order-none">
                    {{ $t('common.close') }}
                </BaseButton>
            </div>
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
import { computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { usePwaReloadBlocker } from '@/pwaReloadGuard'
import { isMessageContentTooLong, MESSAGE_CONTENT_MAX_LENGTH } from '@/utils/messageValidation'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
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
    messageDetailLoading,
    messageDetailError,
    conversationLoading,
    conversationError,
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
    retryMessageDetail,
    retryConversation,
    deleteSelectedMessages,
    startReply,
    cancelInlineReply,
    sendReply,
} = useMailboxResource()

function removePartnerIdFromRoute() {
    if (route.query.partnerId === undefined) return
    const { partnerId: _partnerId, ...query } = route.query
    void router.replace({ query })
}

function closeConversationAndSyncRoute() {
    closeConversation()
    removePartnerIdFromRoute()
}

const replyContentError = computed(() => isMessageContentTooLong(replyContent.value)
    ? t('user.message.contentTooLong', { max: MESSAGE_CONTENT_MAX_LENGTH })
    : '')
usePwaReloadBlocker(computed(() => replyContent.value.trim().length > 0))

type ConversationMessage = MailboxMessageViewModel & { isCurrent: boolean }

const selectedPartnerLabel = computed(() => {
    if (viewType.value === 'received') return t('user.message.from')
    if (viewType.value === 'sent') return t('user.message.to')
    return t('user.message.conversation')
})

const conversationMessages = computed<ConversationMessage[]>(() => {
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
        .map((message) => ({
            ...message,
            isCurrent: message.id === selected.id,
        }))
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

defineExpose({
    startReply,
    replyContent,
    sendReply,
})
</script>

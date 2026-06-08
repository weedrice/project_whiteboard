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
            <span
                class="isolate inline-flex rounded-lg sm:rounded-md shadow-sm w-full sm:w-auto"
                role="group"
                :aria-label="$t('user.message.boxTitle')"
            >
                <button type="button" :aria-pressed="viewType === 'received'" @click="changeViewType('received')"
                    class="flex-1 sm:flex-initial relative inline-flex items-center justify-center rounded-l-lg sm:rounded-l-md px-3 py-2.5 sm:py-2 text-sm font-medium ring-1 ring-inset min-h-[44px] sm:min-h-0 focus:z-10"
                    :class="viewType === 'received'
                        ? 'message-tab-active'
                        : 'message-tab-inactive'">
                    {{ $t('user.message.received') }}
                </button>
                <button type="button" :aria-pressed="viewType === 'sent'" @click="changeViewType('sent')"
                    class="flex-1 sm:flex-initial relative -ml-px inline-flex items-center justify-center rounded-r-lg sm:rounded-r-md px-3 py-2.5 sm:py-2 text-sm font-medium ring-1 ring-inset min-h-[44px] sm:min-h-0 focus:z-10"
                    :class="viewType === 'sent'
                        ? 'message-tab-active'
                        : 'message-tab-inactive'">
                    {{ $t('user.message.sent') }}
                </button>
            </span>
        </template>

        <ul class="divide-y divide-[var(--nv-border)]">
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
                        <div class="text-[11px] sm:text-xs nv-text-subtle flex-shrink-0">
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

    <BaseModal :isOpen="!!selectedMessage" :title="$t('user.message.title')" @close="selectedMessage = null"
        mobile-full mobile-fit-content size="lg">
        <div v-if="selectedMessage" class="p-4 sm:p-6 space-y-4">
            <div class="flex flex-col sm:flex-row sm:justify-between sm:items-start gap-1 border-b nv-border pb-3">
                <div class="min-w-0">
                    <span class="block text-xs nv-text-subtle">{{ viewType === 'received' ?
                        $t('user.message.from') : $t('user.message.to') }}</span>
                    <span class="text-sm font-medium nv-title truncate block">{{
                        selectedMessage.partnerName }}</span>
                </div>
                <span class="text-xs nv-text-subtle flex-shrink-0">{{ formatDate(selectedMessage.createdAt) }}</span>
            </div>
            <div class="text-sm nv-text whitespace-pre-wrap min-h-[120px] overflow-y-auto max-h-[50vh] sm:max-h-none">
                {{ selectedMessage.body }}
            </div>

            <div class="flex flex-col-reverse sm:flex-row justify-end gap-2 sm:space-x-2 pt-4 border-t nv-border">
                <BaseButton v-if="viewType === 'received'" @click="startReply(selectedMessage)"
                    class="w-full sm:w-auto min-h-[44px] order-2 sm:order-none">
                    {{ $t('user.message.reply') }}
                </BaseButton>
                <BaseButton @click="selectedMessage = null" variant="secondary"
                    class="w-full sm:w-auto min-h-[44px] order-1 sm:order-none">
                    {{ $t('common.close') }}
                </BaseButton>
            </div>
        </div>
    </BaseModal>

    <BaseModal :isOpen="isReplyModalOpen" :title="$t('user.message.replyTitle')" @close="closeReplyModal"
        mobile-full mobile-fit-content>
        <div class="p-4 sm:p-6 space-y-4">
            <div>
                <label class="block text-sm font-medium nv-text-muted">{{ $t('user.message.to')
                    }}</label>
                <div class="mt-1 p-3 nv-surface-muted rounded-lg text-sm nv-title">
                    {{ replyTarget?.partnerName }}
                </div>
            </div>
            <div>
                <BaseTextarea v-model="replyContent" :label="$t('user.message.content')" rows="5"
                    class="min-h-[120px] sm:min-h-0" />
            </div>
            <div class="flex flex-col-reverse sm:flex-row justify-end gap-2 sm:gap-2 pt-2">
                <BaseButton @click="sendReply" :disabled="isSending"
                    class="w-full sm:w-auto min-h-[44px] order-2 sm:order-none">
                    {{ isSending ? $t('common.messages.sending') : $t('common.send') }}
                </BaseButton>
                <BaseButton @click="closeReplyModal" variant="secondary"
                    class="w-full sm:w-auto min-h-[44px] order-1 sm:order-none">
                    {{ $t('common.cancel') }}
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
import PaginatedListCard from '@/components/common/ui/PaginatedListCard.vue'
import { Mail } from 'lucide-vue-next'
import { useMailboxResource } from '@/composables/useMailboxResource'
import { formatDate } from '@/utils/date'

const {
    viewType,
    messages,
    loading,
    error,
    selectedMessage,
    selectedMessages,
    page,
    size,
    totalPages,
    isReplyModalOpen,
    replyTarget,
    replyContent,
    isSending,
    fetchMessages,
    handlePageChange,
    handleSizeChange,
    changeViewType,
    openMessage,
    deleteSelectedMessages,
    startReply,
    closeReplyModal,
    sendReply,
} = useMailboxResource()
</script>

<style scoped>
.message-tab-active {
    background: var(--nv-accent);
    border-color: var(--nv-accent);
    color: #fff;
}

.message-tab-active:hover {
    background: color-mix(in srgb, var(--nv-accent) 88%, black 12%);
}

.message-tab-inactive {
    background: var(--nv-surface);
    border-color: var(--nv-border-strong);
    color: var(--nv-text);
}

.message-tab-inactive:hover {
    background: var(--nv-surface-hover);
}
</style>

<template>
    <div class="max-w-4xl mx-auto py-4 sm:py-6 md:py-8 px-4 sm:px-6 lg:px-8">
        <div class="bg-white dark:bg-gray-800 shadow overflow-hidden sm:rounded-lg transition-colors duration-200">
            <div class="px-4 py-4 sm:py-5 sm:px-6 flex flex-col gap-3 border-b border-gray-200 dark:border-gray-700">
                <div class="flex flex-row justify-between items-center gap-2">
                    <h3 class="text-lg leading-6 font-medium text-gray-900 dark:text-white flex items-center min-w-0">
                        <Mail class="h-5 w-5 mr-2 text-gray-500 dark:text-gray-400 flex-shrink-0" />
                        {{ $t('user.message.boxTitle') }}
                    </h3>
                    <div class="flex items-center gap-2 flex-shrink-0">
                        <div class="hidden sm:block">
                            <PageSizeSelector v-model="size" @change="handleSizeChange" />
                        </div>
                        <BaseButton v-if="selectedMessages.length > 0" @click="deleteSelectedMessages" variant="danger"
                            size="sm" class="min-h-[36px] sm:min-h-0 text-xs sm:text-sm">
                            {{ $t('common.delete') }} ({{ selectedMessages.length }})
                        </BaseButton>
                    </div>
                </div>
                <span class="isolate inline-flex rounded-lg sm:rounded-md shadow-sm w-full sm:w-auto" role="group">
                    <button type="button" @click="changeViewType('received')"
                        class="flex-1 sm:flex-initial relative inline-flex items-center justify-center rounded-l-lg sm:rounded-l-md px-3 py-2.5 sm:py-2 text-sm font-medium ring-1 ring-inset min-h-[44px] sm:min-h-0 focus:z-10"
                        :class="viewType === 'received'
                            ? 'bg-indigo-600 text-white ring-indigo-600 hover:bg-indigo-700'
                            : 'bg-white text-gray-900 ring-gray-300 hover:bg-gray-50 dark:bg-gray-800 dark:text-gray-200 dark:ring-gray-600 dark:hover:bg-gray-700'">
                        {{ $t('user.message.received') }}
                    </button>
                    <button type="button" @click="changeViewType('sent')"
                        class="flex-1 sm:flex-initial relative -ml-px inline-flex items-center justify-center rounded-r-lg sm:rounded-r-md px-3 py-2.5 sm:py-2 text-sm font-medium ring-1 ring-inset min-h-[44px] sm:min-h-0 focus:z-10"
                        :class="viewType === 'sent'
                            ? 'bg-indigo-600 text-white ring-indigo-600 hover:bg-indigo-700'
                            : 'bg-white text-gray-900 ring-gray-300 hover:bg-gray-50 dark:bg-gray-800 dark:text-gray-200 dark:ring-gray-600 dark:hover:bg-gray-700'">
                        {{ $t('user.message.sent') }}
                    </button>
                </span>
            </div>

        <div v-if="loading" class="divide-y divide-gray-200 dark:divide-gray-700">
            <div v-for="i in 5" :key="i" class="p-4 flex items-start">
                <BaseSkeleton width="20px" height="20px" className="mr-4 mt-1" />
                <div class="flex-1">
                    <div class="flex justify-between mb-1">
                        <BaseSkeleton width="100px" height="16px" />
                        <BaseSkeleton width="80px" height="12px" />
                    </div>
                    <BaseSkeleton width="80%" height="16px" />
                </div>
            </div>
        </div>

        <EmptyState 
            v-else-if="messages.length === 0"
            :title="$t('user.message.empty')"
            :icon="Mail"
        />

        <ul v-else class="divide-y divide-gray-200 dark:divide-gray-700">
            <li v-for="msg in messages" :key="msg.messageId"
                class="p-3 sm:p-4 hover:bg-gray-50 dark:hover:bg-gray-700 cursor-pointer flex items-start transition-colors duration-200 min-h-[52px] active:bg-gray-100 dark:active:bg-gray-600"
                @click="openMessage(msg)">
                <div class="flex items-center justify-center h-full mr-3 sm:mr-4 p-2 -ml-1 cursor-pointer flex-shrink-0" @click.stop>
                    <BaseCheckbox :value="msg.messageId" v-model="selectedMessages" />
                </div>
                <div class="flex-1 min-w-0">
                    <div class="flex justify-between items-baseline gap-2">
                        <div class="text-sm font-medium text-indigo-600 dark:text-indigo-400 truncate">
                            {{ msg.partner.displayName }}
                        </div>
                        <div class="text-[11px] sm:text-xs text-gray-500 dark:text-gray-400 flex-shrink-0">
                            {{ formatDate(msg.createdAt) }}
                        </div>
                    </div>
                    <p class="mt-0.5 text-xs sm:text-sm text-gray-900 dark:text-gray-100 line-clamp-2"
                        :class="{ 'font-bold': viewType === 'received' && !msg.isRead }">
                        {{ msg.content }}
                    </p>
                </div>
            </li>
        </ul>

        <div v-if="messages.length > 0" class="bg-gray-50 dark:bg-gray-900/50 px-4 py-4 sm:px-6 flex justify-center">
            <Pagination :current-page="page" :total-pages="totalPages" @page-change="handlePageChange" />
        </div>

        <!-- Message Detail Modal -->
        <BaseModal :isOpen="!!selectedMessage" :title="$t('user.message.title')" @close="selectedMessage = null"
            mobile-full mobile-fit-content size="lg">
            <div v-if="selectedMessage" class="p-4 sm:p-6 space-y-4">
                <div class="flex flex-col sm:flex-row sm:justify-between sm:items-start gap-1 border-b dark:border-gray-700 pb-3">
                    <div class="min-w-0">
                        <span class="block text-xs text-gray-500 dark:text-gray-400">{{ viewType === 'received' ?
                            $t('user.message.from') : $t('user.message.to') }}</span>
                        <span class="text-sm font-medium text-gray-900 dark:text-white truncate block">{{
                            selectedMessage.partner.displayName }}</span>
                    </div>
                    <span class="text-xs text-gray-500 dark:text-gray-400 flex-shrink-0">{{ formatDate(selectedMessage.createdAt) }}</span>
                </div>
                <div class="text-sm text-gray-900 dark:text-gray-100 whitespace-pre-wrap min-h-[120px] overflow-y-auto max-h-[50vh] sm:max-h-none">
                    {{ selectedMessage.content }}
                </div>

                <div class="flex flex-col-reverse sm:flex-row justify-end gap-2 sm:space-x-2 pt-4 border-t dark:border-gray-700">
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

        <!-- Reply Modal -->
        <BaseModal :isOpen="isReplyModalOpen" :title="$t('user.message.replyTitle')" @close="closeReplyModal"
            mobile-full mobile-fit-content>
            <div class="p-4 sm:p-6 space-y-4">
                <div>
                    <label class="block text-sm font-medium text-gray-700 dark:text-gray-300">{{ $t('user.message.to')
                        }}</label>
                    <div class="mt-1 p-3 bg-gray-50 dark:bg-gray-700 rounded-lg text-sm text-gray-900 dark:text-white">
                        {{ replyTarget?.partner.displayName }}
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

        </div>
    </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import { messageApi, BLOCKED_BY_USER_CODE } from '@/api/message'
import BaseModal from '@/components/common/ui/BaseModal.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseCheckbox from '@/components/common/ui/BaseCheckbox.vue'
import BaseTextarea from '@/components/common/ui/BaseTextarea.vue'
import BaseSkeleton from '@/components/common/ui/BaseSkeleton.vue'
import EmptyState from '@/components/common/ui/EmptyState.vue'
import Pagination from '@/components/common/ui/Pagination.vue'
import PageSizeSelector from '@/components/common/widgets/PageSizeSelector.vue'
import { Mail } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import { useToastStore } from '@/stores/toast'
import type { Message } from '@/types'
import type { AxiosError } from 'axios'
import { useConfirm } from '@/composables/useConfirm'
import { extractErrorResponse } from '@/utils/errorHandler'
import logger from '@/utils/logger'
import { formatDate } from '@/utils/date'

const NOT_FOUND_CODE = 'C006'
const { t } = useI18n()
const toastStore = useToastStore()
const { confirm } = useConfirm()

const viewType = ref<'received' | 'sent'>('received')
const messages = ref<Message[]>([])
const loading = ref(false)
const selectedMessage = ref<Message | null>(null)
const selectedMessages = ref<number[]>([])

const page = ref(0)
const size = ref(15)
const totalPages = ref(0)

const isReplyModalOpen = ref(false)
const replyTarget = ref<Message | null>(null)
const replyContent = ref('')
const isSending = ref(false)
/** 차단 관계로 인해 상세/읽음 API가 실패한 쪽지인지 (답장 클릭 시에만 토스트 표시용) */
const messageFromBlockedUser = ref(false)
let messageListRequestId = 0
let messageDetailRequestId = 0

async function fetchMessages() {
    const requestId = ++messageListRequestId
    loading.value = true
    messages.value = []
    selectedMessages.value = []
    try {
        const params = {
            page: page.value,
            size: size.value
        }
        const { data } = viewType.value === 'received'
            ? await messageApi.getReceivedMessages(params)
            : await messageApi.getSentMessages(params)

        if (requestId === messageListRequestId && data.success) {
            messages.value = data.data?.content || []
            totalPages.value = data.data?.totalPages || 0
        }
    } catch (error) {
        if (requestId === messageListRequestId) {
            logger.error('Failed to fetch messages:', error)
        }
    } finally {
        if (requestId === messageListRequestId) {
            loading.value = false
        }
    }
}

function handlePageChange(newPage: number) {
    page.value = newPage
    fetchMessages()
}

function handleSizeChange() {
    page.value = 0
    fetchMessages()
}

function changeViewType(type: 'received' | 'sent') {
    if (viewType.value === type) return
    viewType.value = type
    page.value = 0
    fetchMessages()
}

async function openMessage(msg: Message) {
    const requestId = ++messageDetailRequestId
    const messageId = msg.messageId
    messageFromBlockedUser.value = false
    selectedMessage.value = msg
    try {
        const { data } = await messageApi.getMessage(messageId, { skipGlobalErrorHandler: true })
        if (requestId !== messageDetailRequestId || selectedMessage.value?.messageId !== messageId) {
            return
        }
        if (data.success && data.data) {
            selectedMessage.value = data.data
        }
        if (viewType.value === 'received' && !msg.isRead) {
            await messageApi.markAsRead(messageId, { skipGlobalErrorHandler: true })
            msg.isRead = true
            if (requestId !== messageDetailRequestId || selectedMessage.value?.messageId !== messageId) {
                return
            }
            if (selectedMessage.value?.messageId === messageId) {
                selectedMessage.value = { ...selectedMessage.value, isRead: true }
            }
        }
    } catch (error) {
        if (requestId !== messageDetailRequestId || selectedMessage.value?.messageId !== messageId) {
            return
        }
        const errRes = extractErrorResponse(error as AxiosError)
        if (errRes?.code === BLOCKED_BY_USER_CODE) {
            messageFromBlockedUser.value = true
        } else if (errRes?.code === NOT_FOUND_CODE) {
            selectedMessage.value = null
            await fetchMessages()
            toastStore.addToast(t('common.messages.notFound'), 'info')
        } else {
            logger.error('Failed to open message:', error)
        }
    }
}

async function deleteSelectedMessages() {
    const isConfirmed = await confirm(t('common.messages.confirmDelete'))
    if (!isConfirmed) return
    try {
        const { data } = await messageApi.deleteMessages(selectedMessages.value)
        if (data.success) {
            toastStore.addToast(t('common.messages.deleteSuccess'), 'success')
            fetchMessages()
        }
    } catch (error) {
        logger.error('Failed to delete messages:', error)
        toastStore.addToast(t('common.messages.deleteFailed'), 'error')
    }
}

function startReply(msg: Message) {
    if (messageFromBlockedUser.value) {
        toastStore.addToast(t('user.message.blockedByUser'), 'error')
        return
    }
    replyTarget.value = msg
    selectedMessage.value = null
    isReplyModalOpen.value = true
}

function closeReplyModal() {
    isReplyModalOpen.value = false
    replyTarget.value = null
    replyContent.value = ''
}

async function sendReply() {
    if (!replyContent.value.trim()) return
    if (!replyTarget.value) return
    isSending.value = true
    try {
        const { data } = await messageApi.sendMessage(
            replyTarget.value.partner.userId,
            replyContent.value,
            { skipGlobalErrorHandler: true }
        )
        if (data.success) {
            toastStore.addToast(t('user.message.sendSuccess'), 'success')
            closeReplyModal()
        }
    } catch (error) {
        logger.error('Failed to send reply:', error)
        const errRes = extractErrorResponse(error as AxiosError)
        const toastMessage = errRes?.code === BLOCKED_BY_USER_CODE
            ? t('user.message.blockedByUser')
            : t('user.message.sendFailed')
        toastStore.addToast(toastMessage, 'error')
    } finally {
        isSending.value = false
    }
}

watch(selectedMessage, (val) => {
    if (!val) messageFromBlockedUser.value = false
})

onMounted(() => {
    fetchMessages()
})
</script>

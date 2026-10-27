import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, nextTick } from 'vue'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, disposePinia, setActivePinia } from 'pinia'
import { AxiosError, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import api, { configureApiStoreResolvers } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { useConfirmStore } from '@/stores/confirm'
import { useToastStore } from '@/stores/toast'
import { configureAuthQueryScope, notifyAuthSessionBoundary } from '@/queryAuthScope'
import { createDeferred } from '@/test/async'
import type { MessageSummaryDto, Notification, User } from '@/types'
import { emitMessageStreamEvent } from '../messageStreamEvents'
import { useMailboxResource } from '../useMailboxResource'

vi.mock('@/router', () => ({ default: { push: vi.fn() } }))

const adapter = vi.fn()
const oldAdapter = api.defaults.adapter
let pinia: ReturnType<typeof createPinia>
let auth: ReturnType<typeof useAuthStore>
let confirmation: ReturnType<typeof useConfirmStore>
let toast: ReturnType<typeof useToastStore>['addToast']
let wrapper: VueWrapper
let mailbox: ReturnType<typeof useMailboxResource>
let rows: MessageSummaryDto[]

function response(config: InternalAxiosRequestConfig, data: unknown): AxiosResponse {
  return { config, status: 200, statusText: 'OK', headers: {}, data: { success: true, data } }
}

function deleteCalls(): InternalAxiosRequestConfig[] {
  return adapter.mock.calls.map(([config]) => config as InternalAxiosRequestConfig)
    .filter(config => config.method === 'delete')
}

async function mountMailbox() {
  wrapper = mount(defineComponent({
    setup() {
      mailbox = useMailboxResource()
      return () => null
    },
  }), { global: { plugins: [pinia] } })
  await flushPromises()
  expect(mailbox.messages.value.map(message => message.id)).toEqual([51, 52])
  mailbox.selectedMessages.value = [51]
}

function changeSession() {
  auth.sessionGeneration += 1
  auth.user = { userId: 8 } as User
  auth.accessToken = 'new-session-token'
  notifyAuthSessionBoundary(auth.sessionGeneration)
}

beforeEach(() => {
  adapter.mockReset()
  pinia = createPinia()
  setActivePinia(pinia)
  auth = useAuthStore(pinia)
  auth.user = { userId: 7 } as User
  auth.accessToken = 'current-session-token'
  configureApiStoreResolvers({ resolveAuthStore: () => auth })
  configureAuthQueryScope(() => auth.sessionGeneration, () => auth.user?.userId ?? null)
  confirmation = useConfirmStore(pinia)
  toast = vi.spyOn(useToastStore(pinia), 'addToast').mockImplementation(() => '')
  api.defaults.adapter = adapter
  rows = [51, 52].map(messageId => ({
    messageId, content: `Message ${messageId}`, isRead: false,
    partner: { userId: 100, displayName: 'Partner' }, createdAt: '2026-10-27T00:00:00',
  }))
  adapter.mockImplementation(async (config: InternalAxiosRequestConfig) => {
    if (config.method === 'delete') {
      const ids = JSON.parse(config.data as string) as number[]
      rows = rows.filter(row => !ids.includes(row.messageId))
      return response(config, null)
    }
    return response(config, {
      content: rows, page: 0, size: 15, totalPages: rows.length ? 1 : 0,
      totalElements: rows.length, hasNext: false, hasPrevious: false,
    })
  })
})

afterEach(() => {
  wrapper?.unmount()
  disposePinia(pinia)
  configureAuthQueryScope(() => 0)
  configureApiStoreResolvers({ resolveAuthStore: () => null })
  api.defaults.adapter = oldAdapter
  vi.restoreAllMocks()
})

describe('mailbox deletion with real Vue, confirmation, SSE events and Axios', () => {
  it('deletes the originally selected IDs when SSE refresh clears selection during confirmation', async () => {
    await mountMailbox()
    const generation = auth.sessionGeneration
    const deleting = mailbox.deleteSelectedMessages()
    expect(confirmation.isOpen).toBe(true)

    emitMessageStreamEvent({
      notificationId: 91, notificationType: 'MESSAGE', actor: { userId: 100 },
    } as Notification)
    await flushPromises()
    expect(mailbox.selectedMessages.value).toEqual([])
    expect(adapter).toHaveBeenCalledTimes(2)

    confirmation.confirm()
    await deleting

    expect(deleteCalls()).toHaveLength(1)
    expect(JSON.parse(deleteCalls()[0]!.data as string)).toEqual([51])
    expect(deleteCalls()[0]!.expectedSession).toEqual({ sessionGeneration: generation, userId: 7 })
    expect(mailbox.messages.value.map(message => message.id)).toEqual([52])
    expect(toast).toHaveBeenCalledExactlyOnceWith(expect.any(String), 'success')
  })

  it('does nothing when no IDs are selected', async () => {
    await mountMailbox()
    mailbox.selectedMessages.value = []
    await mailbox.deleteSelectedMessages()
    expect(confirmation.isOpen).toBe(false)
    expect(deleteCalls()).toEqual([])
    expect(toast).not.toHaveBeenCalled()
  })

  it('cancels without deleting and allows a subsequent normal deletion', async () => {
    await mountMailbox()
    const cancelled = mailbox.deleteSelectedMessages()
    confirmation.cancel()
    await cancelled
    expect(deleteCalls()).toEqual([])
    expect(toast).not.toHaveBeenCalled()
    const deleting = mailbox.deleteSelectedMessages()
    confirmation.confirm()
    await deleting
    expect(JSON.parse(deleteCalls()[0]!.data as string)).toEqual([51])
    expect(mailbox.messages.value.map(message => message.id)).toEqual([52])
  })

  it('allows only one confirmation and request until the whole deletion settles', async () => {
    await mountMailbox()
    const open = vi.spyOn(confirmation, 'open')
    const deferred = createDeferred<AxiosResponse>()
    adapter.mockImplementationOnce(() => deferred.promise)
    const deleting = mailbox.deleteSelectedMessages()
    await mailbox.deleteSelectedMessages()
    expect(open).toHaveBeenCalledOnce()
    confirmation.confirm()
    await flushPromises()
    await mailbox.deleteSelectedMessages()
    expect(open).toHaveBeenCalledOnce()
    expect(deleteCalls()).toHaveLength(1)
    deferred.resolve(response(deleteCalls()[0]!, null))
    await deleting
  })

  it.each(['session', 'unmount'] as const)('does not send after %s during confirmation', async (boundary) => {
    await mountMailbox()
    const deleting = mailbox.deleteSelectedMessages()
    if (boundary === 'session') changeSession()
    else wrapper.unmount()
    confirmation.confirm()
    await deleting
    expect(deleteCalls()).toEqual([])
    expect(toast).not.toHaveBeenCalled()
  })

  it.each(['session', 'unmount'] as const)('aborts and ignores a delayed response after %s', async (boundary) => {
    await mountMailbox()
    const deferred = createDeferred<AxiosResponse>()
    adapter.mockImplementationOnce(() => deferred.promise)
    const deleting = mailbox.deleteSelectedMessages()
    confirmation.confirm()
    await flushPromises()
    const config = deleteCalls()[0]!
    if (boundary === 'session') changeSession()
    else wrapper.unmount()
    await nextTick()
    expect(config.signal?.aborted).toBe(true)
    deferred.resolve(response(config, null))
    await deleting
    expect(adapter).toHaveBeenCalledTimes(2)
    expect(toast).not.toHaveBeenCalled()
  })

  it('reports a current failure and releases the lock for a retry', async () => {
    await mountMailbox()
    vi.spyOn(console, 'error').mockImplementation(() => {})
    adapter.mockImplementationOnce((config: InternalAxiosRequestConfig) => Promise.reject(
      new AxiosError('Delete failed', 'ERR_BAD_REQUEST', config, undefined, {
        ...response(config, null), status: 400, data: { success: false },
      }),
    ))
    const failed = mailbox.deleteSelectedMessages()
    confirmation.confirm()
    await failed
    expect(toast).toHaveBeenCalledWith(expect.any(String), 'error')
    expect(mailbox.messages.value).toHaveLength(2)
    const retry = mailbox.deleteSelectedMessages()
    confirmation.confirm()
    await retry
    expect(deleteCalls()).toHaveLength(2)
    expect(mailbox.messages.value.map(message => message.id)).toEqual([52])
  })

  it('keeps the action valid through same-session access-token rotation', async () => {
    await mountMailbox()
    const deleting = mailbox.deleteSelectedMessages()
    auth.accessToken = 'rotated-session-token'
    confirmation.confirm()
    await deleting
    expect(deleteCalls()).toHaveLength(1)
    expect(mailbox.messages.value.map(message => message.id)).toEqual([52])
  })
})

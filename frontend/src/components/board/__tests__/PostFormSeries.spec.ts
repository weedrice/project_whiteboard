import { flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { queryClient } from '@/queryClient'
import { createDeferred } from '@/test/async'
import { Storage } from '@/utils/storage'
import {
  findButtonByText,
  mockAddToast,
  mockCreatePostSeries,
  mockGetPostSeries,
  mockPostFormAuthStore,
  mountPostForm,
  resetPostFormTestState,
  unmountPostFormWrappers,
} from './PostFormTestHarness'

const seriesResponse = (seriesId: number, title: string) => ({
  data: {
    data: { seriesId, title },
  },
})

describe('PostForm series creation identity', () => {
  beforeEach(() => {
    resetPostFormTestState()
  })

  afterEach(() => {
    unmountPostFormWrappers()
    vi.restoreAllMocks()
  })

  it('ignores a late success from the previous form while preserving the current request state', async () => {
    const previousRequest = createDeferred<ReturnType<typeof seriesResponse>>()
    const currentRequest = createDeferred<ReturnType<typeof seriesResponse>>()
    mockCreatePostSeries
      .mockReturnValueOnce(previousRequest.promise)
      .mockReturnValueOnce(currentRequest.promise)
    const setQueryData = vi.spyOn(queryClient, 'setQueryData')
    const wrapper = mountPostForm('create')

    await wrapper.get('#new-series').setValue('Previous series')
    await findButtonByText(wrapper, 'board.writePost.createSeries').trigger('click')
    const previousSignal = mockCreatePostSeries.mock.calls[0][1].signal as AbortSignal

    await wrapper.setProps({ boardUrl: 'another-board' })
    expect(previousSignal.aborted).toBe(true)

    await wrapper.get('#new-series').setValue('Current series')
    await findButtonByText(wrapper, 'board.writePost.createSeries').trigger('click')
    expect(wrapper.get('#new-series').attributes('disabled')).toBeDefined()

    previousRequest.resolve(seriesResponse(101, 'Previous series'))
    await flushPromises()

    expect(wrapper.get('#new-series').attributes('disabled')).toBeDefined()
    expect((wrapper.get('#new-series').element as HTMLInputElement).value).toBe('Current series')
    expect(wrapper.text()).not.toContain('Previous series')
    expect(mockAddToast).not.toHaveBeenCalled()
    expect(setQueryData).not.toHaveBeenCalled()

    currentRequest.resolve(seriesResponse(202, 'Current series'))
    await flushPromises()

    expect((wrapper.get('#series').element as HTMLSelectElement).value).toBe('202')
    expect((wrapper.get('#new-series').element as HTMLInputElement).value).toBe('')
    expect(mockAddToast).toHaveBeenCalledWith('board.writePost.createSeriesSuccess', 'success')
    expect(setQueryData).toHaveBeenCalledOnce()
  })

  it('ignores a late failure after the form identity changes', async () => {
    const previousRequest = createDeferred<ReturnType<typeof seriesResponse>>()
    mockCreatePostSeries.mockReturnValueOnce(previousRequest.promise)
    const wrapper = mountPostForm('create')

    await wrapper.get('#new-series').setValue('Previous series')
    await findButtonByText(wrapper, 'board.writePost.createSeries').trigger('click')
    const previousSignal = mockCreatePostSeries.mock.calls[0][1].signal as AbortSignal

    await wrapper.setProps({ boardUrl: 'another-board' })
    await wrapper.get('#new-series').setValue('Current draft title')
    expect(previousSignal.aborted).toBe(true)

    previousRequest.reject(new Error('late failure'))
    await flushPromises()

    expect((wrapper.get('#new-series').element as HTMLInputElement).value).toBe('Current draft title')
    expect(wrapper.get('#new-series').attributes('disabled')).toBeUndefined()
    expect(mockAddToast).not.toHaveBeenCalledWith('board.writePost.createSeriesFailed', 'error')
  })

  it('clears a restored series that is no longer available after options load', async () => {
    mockPostFormAuthStore({
      isAuthenticated: true,
      user: { userId: 1, role: 'USER' },
    })
    mockGetPostSeries.mockResolvedValueOnce({
      data: { data: [{ seriesId: 10, title: 'Available series' }] },
    })
    Storage.set('noviis:draft:1:create:free:new', {
      draftId: 91,
      boardUrl: 'free',
      title: 'Recovered draft',
      contents: 'Recovered body',
      seriesId: 99,
      clientModifiedAt: '2026-08-03T00:00:00.000Z',
      hasLocalChanges: true,
    })

    const wrapper = mountPostForm('create', {}, {}, { postId: '' })
    await flushPromises()

    expect((wrapper.get('#series').element as HTMLSelectElement).value).toBe('')
    expect(mockAddToast).toHaveBeenCalledWith(
      'board.writePost.draftStatus.referencesReset',
      'warning',
    )
  })
})

import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import AdminModalContentState from '../AdminModalContentState.vue'

describe('AdminModalContentState', () => {
  it('prioritizes loading, error, empty, then default content', () => {
    expect(mount(AdminModalContentState, {
      props: { loading: true, error: new Error('failed'), empty: true, loadingText: 'loading' },
      slots: { default: 'content' },
    }).text()).toContain('loading')

    expect(mount(AdminModalContentState, {
      props: { error: new Error('failed'), empty: true, errorText: 'failed' },
      slots: { default: 'content' },
    }).text()).toContain('failed')

    expect(mount(AdminModalContentState, {
      props: { empty: true, emptyText: 'empty' },
      slots: { default: 'content' },
    }).text()).toContain('empty')

    expect(mount(AdminModalContentState, {
      slots: { default: 'content' },
    }).text()).toContain('content')
  })

  it('announces loading, error, and empty states with matching urgency', () => {
    const loading = mount(AdminModalContentState, {
      props: { loading: true, loadingText: 'loading' },
    })
    expect(loading.get('[role="status"]').attributes('aria-busy')).toBe('true')
    expect(loading.get('[role="status"]').attributes('aria-live')).toBe('polite')

    const error = mount(AdminModalContentState, {
      props: { error: true, errorText: 'failed' },
    })
    expect(error.get('[role="alert"]').attributes('aria-live')).toBe('assertive')

    const empty = mount(AdminModalContentState, {
      props: { empty: true, emptyText: 'empty' },
    })
    expect(empty.get('[role="status"]').attributes('aria-live')).toBe('polite')
  })

  it('preserves state semantics when custom slots are provided', () => {
    const loading = mount(AdminModalContentState, {
      props: { loading: true },
      slots: { loading: '<span>Custom loading</span>' },
    })
    expect(loading.get('[role="status"]').text()).toBe('Custom loading')

    const error = mount(AdminModalContentState, {
      props: { error: true },
      slots: { error: '<span>Custom error</span>' },
    })
    expect(error.get('[role="alert"]').text()).toBe('Custom error')

    const empty = mount(AdminModalContentState, {
      props: { empty: true },
      slots: { empty: '<span>Custom empty</span>' },
    })
    expect(empty.get('[role="status"]').text()).toBe('Custom empty')
  })
})

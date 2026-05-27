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
})

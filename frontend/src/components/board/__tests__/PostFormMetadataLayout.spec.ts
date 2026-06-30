import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import {
  mountPostForm,
  resetPostFormTestState,
  unmountPostFormWrappers,
} from './PostFormTestHarness'

describe('PostForm metadata layout', () => {
  afterEach(() => {
    unmountPostFormWrappers()
  })

  beforeEach(() => {
    resetPostFormTestState()
  })

  it('renders the desktop metadata cards for the compose shell', () => {
    const wrapper = mountPostForm('create')

    expect(wrapper.findAll('.nv-compose-side-card').length).toBeGreaterThanOrEqual(2)
    expect(wrapper.find('aside').classes()).toContain('lg:sticky')
  })

  it('renders create and edit titles', () => {
    const createWrapper = mountPostForm('create')
    const editWrapper = mountPostForm('edit')

    expect(createWrapper.text()).toContain('board.writePost.createTitle')
    expect(editWrapper.text()).toContain('board.writePost.editTitle')
  })

  it('renders overridden create title when provided', () => {
    const wrapper = mountPostForm('create', {}, {}, {
      createTitleOverride: '문의 작성',
    })

    expect(wrapper.text()).toContain('문의 작성')
  })

  it('hides board label and preview action when configured', () => {
    const wrapper = mountPostForm('create', {}, {}, {
      hideBoardLabel: true,
      hidePreview: true,
    })

    expect(wrapper.text()).not.toContain('free')
    expect(wrapper.text()).not.toContain('board.writePost.actions.preview')
  })

  it('connects mobile and desktop tag labels to unique inputs', () => {
    const wrapper = mountPostForm('create')
    const mobileTagInput = wrapper.get('#post-tags-input-mobile')
    const desktopTagInput = wrapper.get('#post-tags-input-desktop')

    expect(wrapper.findAll('#post-tags-input-mobile')).toHaveLength(1)
    expect(wrapper.findAll('#post-tags-input-desktop')).toHaveLength(1)
    expect(wrapper.get('label[for="post-tags-input-mobile"]').attributes('for')).toBe('post-tags-input-mobile')
    expect(wrapper.get('label[for="post-tags-input-desktop"]').attributes('for')).toBe('post-tags-input-desktop')
    expect(mobileTagInput.attributes('id')).toBe('post-tags-input-mobile')
    expect(desktopTagInput.attributes('id')).toBe('post-tags-input-desktop')
  })

  it('connects mobile and desktop category labels to unique selects', () => {
    const wrapper = mountPostForm('create')
    const mobileCategorySelect = wrapper.get('#category-mobile')
    const desktopCategorySelect = wrapper.get('#category')

    expect(wrapper.findAll('#category-mobile')).toHaveLength(1)
    expect(wrapper.findAll('#category')).toHaveLength(1)
    expect(wrapper.get('label[for="category-mobile"]').text()).toBe('common.category')
    expect(wrapper.get('label[for="category"]').text()).toBe('common.category')
    expect(mobileCategorySelect.element.tagName).toBe('SELECT')
    expect(desktopCategorySelect.element.tagName).toBe('SELECT')
  })
})

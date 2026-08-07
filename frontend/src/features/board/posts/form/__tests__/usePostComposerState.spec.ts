import { describe, expect, it } from 'vitest'
import { ref } from 'vue'
import { usePostComposerState } from '../usePostComposerState'
import { encodeSandboxedPostHtml } from '@/utils/postHtmlSandbox'

function createComposer() {
  return usePostComposerState({
    mode: () => 'create',
    hideCategory: () => false,
    hideTags: () => false,
    hideSpoiler: () => false,
    hideSecret: () => false,
    showNotice: ref(true),
    canShowNsfw: ref(true),
  })
}

describe('usePostComposerState', () => {
  it('preserves an incomplete poll only in the draft payload', () => {
    const composer = createComposer()
    composer.form.value.poll = {
      question: '  아직 작성 중  ',
      options: ['첫 번째', ''],
      multipleChoiceEnabled: true,
      anonymousEnabled: false,
      closesAt: null,
    }

    expect(composer.buildPayload()).not.toHaveProperty('poll')
    expect(composer.buildPayload('draft').poll).toEqual({
      question: '  아직 작성 중  ',
      options: ['첫 번째', ''],
      multipleChoiceEnabled: true,
      anonymousEnabled: false,
      closesAt: null,
    })
  })

  it('restores a direct series id when navigation metadata is absent', () => {
    const composer = createComposer()

    composer.applyDraftSnapshot({ seriesId: 42 })

    expect(composer.form.value.seriesId).toBe(42)
  })

  it('restores a malformed legacy poll with safe empty fields', () => {
    const composer = createComposer()

    composer.applyDraftSnapshot({
      poll: {
        question: null,
        options: null,
      },
    })

    expect(composer.form.value.poll).toEqual({
      question: '',
      options: [],
      multipleChoiceEnabled: false,
      anonymousEnabled: false,
      closesAt: null,
    })
  })

  it('does not truncate content surrounding a preserved html marker', () => {
    const composer = createComposer()
    const marker = encodeSandboxedPostHtml('<style>.card{display:grid}</style><p>Widget</p>')
    const mixedContent = `${marker}<p>Tail</p>`

    composer.applyDraftSnapshot({ contents: mixedContent })

    expect(composer.form.value.content).toBe(mixedContent)
  })
})

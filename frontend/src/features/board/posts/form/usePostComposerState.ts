import { computed, ref, type Ref } from 'vue'
import {
  buildPostFormPayload,
  createEmptyPostFormPoll,
  resolvePostFormFileIds,
  type PostFormPoll,
  type PostFormFileIdScope,
} from '@/utils/postForm'
import { decodeSandboxedPostHtml } from '@/utils/postHtmlSandbox'

export type PostComposerMode = 'create' | 'edit'

export type PostComposerFormState = {
  title: string
  content: string
  categoryId: string | number
  tags: string[]
  isNsfw: boolean
  isSpoiler: boolean
  isNotice: boolean
  isSecret: boolean
  seriesId: string | number
  poll: PostFormPoll | null
}

export type PostComposerSnapshot = {
  title?: string
  contents?: string
  categoryId?: number | null
  tags?: string[]
  isNsfw?: boolean
  isSpoiler?: boolean
  isNotice?: boolean
  isSecret?: boolean
  seriesId?: number | null
  seriesNavigation?: { series?: { seriesId?: number | null } | null } | null
  poll?: {
    question: string
    multipleChoiceEnabled?: boolean
    anonymousEnabled?: boolean
    closesAt?: string | null
    options: Array<{ optionText: string } | string>
  } | null
  fileIds?: number[]
}

type UsePostComposerStateOptions = {
  mode: () => PostComposerMode
  hideCategory: () => boolean | undefined
  hideTags: () => boolean | undefined
  hideSpoiler: () => boolean | undefined
  hideSecret: () => boolean | undefined
  showNotice: Ref<boolean>
  canShowNsfw: Ref<boolean>
  includePoll?: () => boolean
}

export function createEmptyPostComposerForm(): PostComposerFormState {
  return {
    title: '',
    content: '',
    categoryId: '',
    tags: [],
    isNsfw: false,
    isSpoiler: false,
    isNotice: false,
    isSecret: false,
    seriesId: '',
    poll: null,
  }
}

function copyFormSnapshot(src: PostComposerFormState): PostComposerFormState {
  return {
    title: src.title,
    content: src.content,
    categoryId: src.categoryId,
    tags: [...src.tags],
    isNsfw: src.isNsfw,
    isSpoiler: src.isSpoiler,
    isNotice: src.isNotice,
    isSecret: src.isSecret,
    seriesId: src.seriesId,
    poll: src.poll ? { ...src.poll, options: [...src.poll.options] } : null,
  }
}

function isSameFormState(left: PostComposerFormState, right: PostComposerFormState): boolean {
  if (
    left.title !== right.title
    || left.content !== right.content
    || left.isNsfw !== right.isNsfw
    || left.isSpoiler !== right.isSpoiler
    || left.isNotice !== right.isNotice
    || left.isSecret !== right.isSecret
  ) {
    return false
  }
  if (String(left.categoryId) !== String(right.categoryId)) return false
  if (String(left.seriesId) !== String(right.seriesId)) return false
  if (JSON.stringify(left.poll) !== JSON.stringify(right.poll)) return false
  if (left.tags.length !== right.tags.length) return false
  return left.tags.every((tag, index) => tag === right.tags[index])
}

export function usePostComposerState(options: UsePostComposerStateOptions) {
  const form = ref<PostComposerFormState>(createEmptyPostComposerForm())
  const draftFileIds = ref<number[]>([])
  const initialFormSnapshot = ref<PostComposerFormState | null>(null)

  function markCurrentSnapshotSaved() {
    initialFormSnapshot.value = copyFormSnapshot(form.value)
  }

  function isFormDirty(): boolean {
    const init = initialFormSnapshot.value
    if (!init) return false
    return !isSameFormState(form.value, init)
  }

  function applyDraftSnapshot(draft: PostComposerSnapshot) {
    form.value = {
      title: draft.title ?? '',
      content: decodeSandboxedPostHtml(draft.contents) ?? draft.contents ?? '',
      categoryId: draft.categoryId ?? '',
      tags: [...(draft.tags ?? [])],
      isNsfw: Boolean(draft.isNsfw),
      isSpoiler: Boolean(draft.isSpoiler),
      isNotice: Boolean(draft.isNotice),
      isSecret: Boolean(draft.isSecret),
      seriesId: draft.seriesNavigation?.series?.seriesId ?? draft.seriesId ?? '',
      poll: draft.poll ? {
        question: draft.poll.question,
        options: draft.poll.options.map((option) => typeof option === 'string' ? option : option.optionText),
        multipleChoiceEnabled: Boolean(draft.poll.multipleChoiceEnabled),
        anonymousEnabled: Boolean(draft.poll.anonymousEnabled),
        closesAt: draft.poll.closesAt ?? null,
      } : null,
    }
    draftFileIds.value = [...(draft.fileIds ?? [])]
  }

  const buildPayload = (fileIdScope: PostFormFileIdScope = 'content') => {
    const payload = buildPostFormPayload({
      form: form.value,
      mode: options.mode(),
      hideCategory: options.hideCategory(),
      hideTags: options.hideTags(),
      hideSpoiler: options.hideSpoiler(),
      hideSecret: options.hideSecret(),
      showNotice: options.showNotice.value,
      canShowNsfw: options.canShowNsfw.value,
      fileIds: resolvePostFormFileIds(form.value.content, draftFileIds.value, fileIdScope),
      includePoll: options.includePoll?.(),
    })
    if (fileIdScope !== 'draft' || !form.value.poll) return payload
    return {
      ...payload,
      poll: {
        question: form.value.poll.question,
        options: [...form.value.poll.options],
        multipleChoiceEnabled: form.value.poll.multipleChoiceEnabled,
        anonymousEnabled: form.value.poll.anonymousEnabled,
        closesAt: form.value.poll.closesAt || null,
      },
    }
  }

  function trackUploadedFile(fileId: number) {
    if (!draftFileIds.value.includes(fileId)) {
      draftFileIds.value.push(fileId)
    }
  }

  function openPollEditor() {
    form.value.poll = form.value.poll ?? createEmptyPostFormPoll()
  }

  function resetFormState() {
    form.value = createEmptyPostComposerForm()
    draftFileIds.value = []
    initialFormSnapshot.value = null
  }

  return {
    form,
    draftFileIds,
    isDirty: computed(() => isFormDirty()),
    isFormDirty,
    markCurrentSnapshotSaved,
    applyDraftSnapshot,
    buildPayload,
    openPollEditor,
    trackUploadedFile,
    resetFormState,
  }
}

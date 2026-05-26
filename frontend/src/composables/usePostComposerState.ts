import { computed, ref, type Ref } from 'vue'
import {
  buildPostFormPayload,
  resolvePostFormFileIds,
  type PostFormFileIdScope,
} from '@/utils/postForm'

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
      content: draft.contents ?? '',
      categoryId: draft.categoryId ?? '',
      tags: [...(draft.tags ?? [])],
      isNsfw: Boolean(draft.isNsfw),
      isSpoiler: Boolean(draft.isSpoiler),
      isNotice: Boolean(draft.isNotice),
      isSecret: Boolean(draft.isSecret),
    }
    draftFileIds.value = [...(draft.fileIds ?? [])]
  }

  const buildPayload = (fileIdScope: PostFormFileIdScope = 'content') => {
    return buildPostFormPayload({
      form: form.value,
      mode: options.mode(),
      hideCategory: options.hideCategory(),
      hideTags: options.hideTags(),
      hideSpoiler: options.hideSpoiler(),
      hideSecret: options.hideSecret(),
      showNotice: options.showNotice.value,
      canShowNsfw: options.canShowNsfw.value,
      fileIds: resolvePostFormFileIds(form.value.content, draftFileIds.value, fileIdScope),
    })
  }

  function trackUploadedFile(fileId: number) {
    if (!draftFileIds.value.includes(fileId)) {
      draftFileIds.value.push(fileId)
    }
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
    trackUploadedFile,
    resetFormState,
  }
}

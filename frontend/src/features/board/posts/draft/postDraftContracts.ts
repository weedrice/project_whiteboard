import type { PostDraftData } from '@/api/post'
import type { DraftPost } from '@/types'
import type { DraftRecoverySnapshot } from '@/features/board/posts/draft/postDraftRecovery'

export interface DraftContentAdapter {
  buildPayload: () => PostDraftData
  applySnapshot: (snapshot: DraftRecoverySnapshot) => void
  normalizeSnapshot: (snapshot: DraftRecoverySnapshot) => DraftRecoverySnapshot
  recoverServerReferences: (savedDraft: DraftPost, payload: PostDraftData) => PostDraftData | void
  canPersist: () => boolean
  selectDetachedUploadIds: (payload: PostDraftData) => number[]
}

export type DraftLifecycleEvent =
  | { type: 'saved', scope: 'server' | 'browser' }
  | { type: 'server-saved', payload: PostDraftData, draft: DraftPost }
  | { type: 'references-removed' }
  | { type: 'local-snapshot-stored', snapshot: DraftRecoverySnapshot }
  | { type: 'local-snapshot-found', snapshot: DraftRecoverySnapshot }
  | { type: 'local-snapshot-deleted' }
  | { type: 'limit-evicted', count: number }

export type DraftSaveResult =
  | { type: 'server', draft: DraftPost }
  | { type: 'browser' }
  | { type: 'cleared' }
  | { type: 'skipped' }

export type DraftRestoreResult =
  | { type: 'applied', source: 'server' | 'local' }
  | { type: 'conflict' }
  | { type: 'protected' }
  | { type: 'missing' }
  | { type: 'ambiguous' }
  | { type: 'failed' }
  | { type: 'cancelled' }

export type DraftActionId =
  | 'save'
  | 'reload-server'
  | 'keep-local'
  | 'retry-restore'
  | 'save-as-new'
  | 'discard-local'
  | 'open-scheduled'

export interface DraftPresentationAction {
  id: DraftActionId
  label: string
  variant: 'primary' | 'secondary'
  disabled: boolean
  to?: string
}

export interface DraftPresentation {
  label: string
  busy: boolean
  actions: DraftPresentationAction[]
}

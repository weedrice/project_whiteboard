import type { Editor } from '@tiptap/core'
import { createImageDownloadQueue } from '@/utils/imageDownloadQueue'

const queues = new WeakMap<Editor, ReturnType<typeof createImageDownloadQueue>>()

export function getPostEditorImageQueue(editor: Editor) {
  let queue = queues.get(editor)
  if (!queue) {
    queue = createImageDownloadQueue()
    queues.set(editor, queue)
  }
  return queue
}

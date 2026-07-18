import type { Ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useConfirm } from '@/composables/useConfirm'
import type { BoardDetail } from '@/types'

interface BoardSubscriptionPayload {
  boardUrl: string
  isSubscribed: boolean
}

interface UseBoardSubscriptionActionOptions {
  board: Ref<BoardDetail | null | undefined>
  sessionGeneration: Readonly<Ref<number>>
  isSubscribePending: Ref<boolean>
  subscribeMutate: (payload: BoardSubscriptionPayload) => void
}

export function useBoardSubscriptionAction({
  board,
  sessionGeneration,
  isSubscribePending,
  subscribeMutate,
}: UseBoardSubscriptionActionOptions) {
  const { t } = useI18n()
  const { confirm } = useConfirm()

  async function handleSubscribe() {
    const currentBoard = board.value
    if (!currentBoard || isSubscribePending.value) return
    const boardUrl = currentBoard.boardUrl
    const subscribed = currentBoard.isSubscribed ?? false
    const generation = sessionGeneration.value

    const isCurrentIntent = () => (
      board.value?.boardUrl === boardUrl
      && (board.value?.isSubscribed ?? false) === subscribed
      && sessionGeneration.value === generation
      && !isSubscribePending.value
    )

    if (subscribed) {
      const isConfirmed = await confirm(t('user.subscriptions.unsubscribeConfirm'))
      if (!isConfirmed || !isCurrentIntent()) return
    }

    if (!isCurrentIntent()) return

    subscribeMutate({
      boardUrl,
      isSubscribed: subscribed,
    })
  }

  return {
    handleSubscribe,
  }
}

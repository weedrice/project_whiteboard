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
  isSubscribePending: Ref<boolean>
  subscribeMutate: (payload: BoardSubscriptionPayload) => void
}

export function useBoardSubscriptionAction({
  board,
  isSubscribePending,
  subscribeMutate,
}: UseBoardSubscriptionActionOptions) {
  const { t } = useI18n()
  const { confirm } = useConfirm()

  async function handleSubscribe() {
    const currentBoard = board.value
    if (!currentBoard || isSubscribePending.value) return

    if (currentBoard.isSubscribed) {
      const isConfirmed = await confirm(t('user.subscriptions.unsubscribeConfirm'))
      if (!isConfirmed) return
    }

    subscribeMutate({
      boardUrl: currentBoard.boardUrl,
      isSubscribed: currentBoard.isSubscribed ?? false,
    })
  }

  return {
    handleSubscribe,
  }
}

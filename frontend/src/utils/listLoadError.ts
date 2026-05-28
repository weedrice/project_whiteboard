export const LIST_LOAD_ERROR_MESSAGE_KEY = 'common.messages.loadFailed'

type Translate = (key: string) => string

const defaultTranslate: Translate = (key) => key

export function getListLoadErrorMessage(t: Translate = defaultTranslate): string {
  return t(LIST_LOAD_ERROR_MESSAGE_KEY)
}

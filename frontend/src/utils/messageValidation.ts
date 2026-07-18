export const MESSAGE_CONTENT_MAX_LENGTH = 5_000

export const hasMessageContent = (content: string) => content.trim().length > 0

export const isMessageContentTooLong = (content: string) => (
  content.length > MESSAGE_CONTENT_MAX_LENGTH
)

export const isValidMessageContent = (content: string) => (
  hasMessageContent(content) && !isMessageContentTooLong(content)
)

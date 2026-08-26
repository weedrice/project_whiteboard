export type InquiryCategory =
  | 'ACCOUNT'
  | 'SERVICE_USE'
  | 'TECHNICAL'
  | 'CONTENT_OPERATION'
  | 'SUGGESTION'
  | 'OTHER'

export type InquiryStatus = 'NEW' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED'
export type InquiryPriority = 'NORMAL' | 'HIGH' | 'URGENT'
export type InquiryMessageType = 'USER_MESSAGE' | 'STAFF_REPLY' | 'INTERNAL_NOTE'
export type InquiryClosureReason = 'WITHDRAWN' | 'USER_CONFIRMED' | 'ADMIN_CLOSED' | 'AUTO_CLOSED'

export interface InquiryAttachment {
  fileId: number
  originalName: string
  fileSize: number
  mimeType: string
  url: string
}

export interface InquiryMessage {
  messageId: number
  authorUserId: number
  authorName: string
  messageType: InquiryMessageType
  content: string
  attachments: InquiryAttachment[]
  createdAt: string
}

export interface InquiryHistory {
  historyId: number
  actionType: string
  fromStatus?: InquiryStatus | null
  toStatus: InquiryStatus
  createdAt: string
}

export interface InquiryAllowedActions {
  canAddMessage: boolean
  canWithdraw: boolean
  canClose: boolean
}

export interface InquirySummary {
  inquiryId: number
  category: InquiryCategory
  title: string
  status: InquiryStatus
  effectivePriority?: InquiryPriority | null
  lastPublicMessageSummary: string
  authorUserId: number
  authorName: string
  staffActionSince?: string | null
  createdAt: string
  modifiedAt: string
}

export interface InquiryDetail {
  inquiryId: number
  authorUserId: number
  authorName: string
  category: InquiryCategory
  title: string
  status: InquiryStatus
  effectivePriority?: InquiryPriority | null
  closureReason?: InquiryClosureReason | null
  closureDetail?: string | null
  allowedActions: InquiryAllowedActions
  messages: InquiryMessage[]
  histories: InquiryHistory[]
  firstRespondedAt?: string | null
  resolvedAt?: string | null
  closedAt?: string | null
  createdAt: string
  modifiedAt: string
}

export interface InquiryCreateData {
  category: InquiryCategory
  title: string
  content: string
  fileIds: number[]
}

export interface InquiryMessageCreateData {
  content: string
  fileIds: number[]
}

export interface InquiryListParams {
  status?: InquiryStatus
  category?: InquiryCategory
  priority?: InquiryPriority
  keyword?: string
  from?: string
  to?: string
  page?: number
  size?: number
  sort?: string
}

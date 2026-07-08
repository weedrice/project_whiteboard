import { userAccountApi } from '@/api/userAccountApi'
import { userActivityApi } from '@/api/userActivityApi'
import { userAgentApi } from '@/api/userAgentApi'

export {
    toBlockedUserListItem,
    toBlockedUserPage,
    toScrapPostSummaryPage,
    type BlockedUserListItem,
    type BlockedUserResponseDto,
} from '@/api/userMappers'
export {
    type KeywordSubscriptionPayload,
    type KeywordSubscriptionResponse,
    type NotificationSettingType,
    type NotificationSettingsBulkPayload,
    type NotificationSettingsPayload,
    type PushSubscriptionPayload,
    type PushSubscriptionResponse,
    type UserUpdatePayload,
} from '@/api/userAccountApi'
export {
    type UserAgent,
    type UserAgentListResponse,
} from '@/api/userAgentApi'

export const userApi = {
    ...userAccountApi,
    ...userAgentApi,
    ...userActivityApi,
}

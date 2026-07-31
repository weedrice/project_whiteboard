import { createUserFeatureContext } from '@/features/user/userFeatureContext'
import {
  createMyProfileQueryOptions,
  useUserProfileFeature,
} from '@/features/user/profile/useUserProfileFeature'
import {
  userSettingsQueryKey,
  userSettingsSessionQueryKey,
  useUserSettingsFeature,
} from '@/features/user/settings/useUserSettingsFeature'
import { useUserSecurityFeature } from '@/features/user/security/useUserSecurityFeature'
import {
  createMyAgentsQueryOptions,
  useUserAgentFeature,
} from '@/features/user/agents/useUserAgentFeature'
import { useUserActivityFeature } from '@/features/user/activity/useUserActivityFeature'

export { userQueryKeys } from '@/features/user/userQueryKeys'
export {
  createMyProfileQueryOptions,
  createMyAgentsQueryOptions,
  userSettingsQueryKey,
  userSettingsSessionQueryKey,
}

export function useUser() {
  const context = createUserFeatureContext()

  return {
    ...useUserProfileFeature(context),
    ...useUserSettingsFeature(context),
    ...useUserSecurityFeature(context),
    ...useUserAgentFeature(context),
    ...useUserActivityFeature(context),
  }
}

import { API_ERROR_CODES } from '@/api/errorCodes'
import { isAxiosError } from 'axios'
import { extractErrorCode } from '@/utils/errorHandler'

export function isProfileSectionPrivate(error: unknown): boolean {
  return isAxiosError(error)
    && error.response?.status === 403
    && extractErrorCode(error) === API_ERROR_CODES.PROFILE_SECTION_PRIVATE
}

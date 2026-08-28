import { readdirSync, readFileSync } from 'node:fs'
import { relative, resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import messages from '../index'
import englishMessages from '../en'

type MessageRecord = Record<string, unknown>

const sourceRoot = resolve(process.cwd(), 'src')
const ignoredPathPattern = /(?:^|\/)(__tests__|test)(?:\/|$)|\.spec\.ts$/
const staticCallPattern = /(?:\$t|\b(?:[\w$]+\.)*t)\(\s*(['"`])([^'"`\r\n$]+)\1/g
const templateCallPattern = /(?:\$t|\b(?:[\w$]+\.)*t)\(\s*`([^`]*\$\{[^`]*)`/g
const dynamicCallPattern = /(?:\$t|\b(?:[\w$]+\.)*t)\(\s*(?!['"`])[^\r\n)]*/

const allowedTemplateCalls = [
  { template: 'user.settings.notificationTypes.${type}.label', keys: /^user\.settings\.notificationTypes\.[^.]+\.label$/ },
  { template: 'user.settings.notificationTypes.${type}.description', keys: /^user\.settings\.notificationTypes\.[^.]+\.description$/ },
  { template: 'user.pointsHistory.transaction.${transactionKind(item)}', keys: /^user\.pointsHistory\.transaction\.[^.]+$/ },
  { template: 'user.draftList.scheduledStatus.${post.status}', keys: /^user\.draftList\.scheduledStatus\.[^.]+$/ },
  { template: 'board.writePost.colorLabels.${colorLabelKeys[index]}', keys: /^board\.writePost\.colorLabels\.[^.]+$/ },
  {
    template: 'board.writePost.imageControls.${item.label}',
    keys: /^board\.writePost\.imageControls\.(alignLeft|alignCenter|alignRight|alignInline)$/,
  },
  { template: 'board.postDetail.versionActions.${normalized}', keys: /^board\.postDetail\.versionActions\.(CREATE|MODIFY|DELETE)$/ },
  { template: 'admin.users.status.${status}', keys: /^admin\.users\.status\.[^.]+$/ },
  { template: 'admin.users.role.${role}', keys: /^admin\.users\.role\.[^.]+$/ },
  { template: 'admin.reports.status.${status}', keys: /^admin\.reports\.status\.[^.]+$/ },
  { template: 'admin.reports.status.${value}', keys: /^admin\.reports\.status\.(PENDING|RESOLVED|REJECTED)$/ },
  { template: 'admin.sanction.types.${value}', keys: /^admin\.sanction\.types\.(WARNING|MUTE|BAN)$/ },
  { template: 'admin.dashboard.auditTargets.${value}', keys: /^admin\.dashboard\.auditTargets\.(POST|COMMENT|REPORT|USER|BOARD|SHOP_ITEM)$/ },
  { template: 'report.types.${targetType.toLowerCase()}', keys: /^report\.types\.[^.]+$/ },
  { template: 'report.reasonTypes.${value}', keys: /^report\.reasonTypes\.(SPAM|ABUSE|ADULT|ETC)$/ },
  { template: 'report.reasonTypes.${reasonType}', keys: /^report\.reasonTypes\.(SPAM|ABUSE|ADULT|ETC)$/ },
  { template: 'shop.itemTypes.${value}', keys: /^shop\.itemTypes\.EMOTICON$/ },
  { template: 'user.pointsHistory.types.${value}', keys: /^user\.pointsHistory\.types\.(EARN|SPEND|EXPIRE|PENALTY|REWARD_REVERSAL)$/ },
  { template: 'inquiry.timeline.${type}', keys: /^inquiry\.timeline\.(USER_MESSAGE|STAFF_REPLY|INTERNAL_NOTE)$/ },
  { template: 'inquiry.category.${value}', keys: /^inquiry\.category\.(ACCOUNT|SERVICE_USE|TECHNICAL|CONTENT_OPERATION|SUGGESTION|OTHER)$/ },
  { template: 'inquiry.status.${value}', keys: /^inquiry\.status\.(NEW|IN_PROGRESS|RESOLVED|CLOSED)$/ },
  { template: 'inquiry.priority.${value}', keys: /^inquiry\.priority\.(NORMAL|HIGH|URGENT)$/ },
  { template: 'inquiry.closureReason.${value}', keys: /^inquiry\.closureReason\.(WITHDRAWN|USER_CONFIRMED|ADMIN_CLOSED|AUTO_CLOSED)$/ },
  { template: 'inquiry.category.${item}', keys: /^inquiry\.category\.(ACCOUNT|SERVICE_USE|TECHNICAL|CONTENT_OPERATION|SUGGESTION|OTHER)$/ },
] as const

const allowedDynamicCallSites: ReadonlyArray<{ file: string; line: RegExp }> = [
  { file: 'features/notifications/notificationPresentation.ts', line: /t\(notification\.(messageKey|actorLabelKey)/ },
  { file: 'components/admin/AdminAuditLogTable.vue', line: /t\(key/ },
  { file: 'views/admin/AdminInquiryPosts.vue', line: /t\(item\.statusLabelKey/ },
  { file: 'components/user/MyPageSummaryCards.vue', line: /\$t\(card\.titleKey/ },
  { file: 'components/user/MyPageNavigation.vue', line: /\$t\((group|item)\.nameKey/ },
  {
    file: 'features/user/settings/NotificationSettingsSection.vue',
    line: /(?:t\(messageKey|\$t\(pushStatusKey)/,
  },
  { file: 'utils/authRedirect.ts', line: /options\.t\(DELETED_ACCOUNT_MESSAGE_KEY/ },
  { file: 'components/notification/NotificationDropdown.vue', line: /t\(getNotificationPresentation/ },
  { file: 'components/layout/UserDropdown.vue', line: /\$t\(item\.label/ },
  { file: 'composables/useAuthEmailVerificationSection.ts', line: /options\.t\(options\.verifyLabelKey/ },
  { file: 'router/resourceAccessGuards.ts', line: /i18n\.global\.t\(BOARD_WRITE_FORBIDDEN_MESSAGE_KEY/ },
  { file: 'views/user/MyNotifications.vue', line: /t\(getNotificationPresentation/ },
  { file: 'features/board/form/useBoardFormSubmit.ts', line: /t\(requiredFieldValidation\.messageKey/ },
  { file: 'utils/listLoadError.ts', line: /t\(LIST_LOAD_ERROR_MESSAGE_KEY/ },
  { file: 'utils/errorHandler.ts', line: /t\(DEFAULT_API_ERROR_MESSAGE_KEY/ },
  { file: 'features/admin/boards/useAdminBoardCreateModal.ts', line: /t\(requiredFieldValidation\.messageKey/ },
  { file: 'components/common/widgets/UserSelectModal.vue', line: /t\(isSelected/ },
  { file: 'components/board/boardPostSearchModel.ts', line: /t\(SEARCH_TYPE_LABEL_KEYS/ },
  { file: 'components/board/editor/PostEditorSlashMenu.vue', line: /t\(actionLabels/ },
  { file: 'components/common/ui/PullToRefresh.vue', line: /\$t\(indicatorLabel/ },
  { file: 'features/board/write/useWriteBoardSheet.ts', line: /i18n\.global\.t\(BOARD_WRITE_(FORBIDDEN|VERIFY_FAILED)_MESSAGE_KEY/ },
  { file: 'features/user/useBadgeTranslation.ts', line: /t\(key/ },
]

function isMessageRecord(value: unknown): value is MessageRecord {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value)
}

function collectLeafKeys(value: unknown, prefix = ''): string[] {
  if (!isMessageRecord(value)) return [prefix]
  return Object.keys(value).flatMap((key) => collectLeafKeys(value[key], prefix ? `${prefix}.${key}` : key))
}

function collectSourceFiles(directory: string): string[] {
  return readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const path = resolve(directory, entry.name)
    if (entry.isDirectory()) return collectSourceFiles(path)
    return /\.(ts|vue)$/.test(entry.name) ? [path] : []
  })
}

function sourceEntries() {
  return collectSourceFiles(sourceRoot)
    .map((file) => ({
      file: relative(sourceRoot, file).replaceAll('\\', '/'),
      source: readFileSync(file, 'utf8'),
    }))
    .filter(({ file }) => !ignoredPathPattern.test(file))
}

describe('i18n key usage guard', () => {
  const sources = sourceEntries()
  const koKeys = collectLeafKeys(messages.ko).sort()
  const enKeys = collectLeafKeys(englishMessages).sort()
  const registeredKeys = new Set(koKeys)

  it('keeps Korean and English leaf keys in parity', () => {
    expect(enKeys).toEqual(koKeys)
  })

  it('registers every statically referenced translation key', () => {
    const missing = sources.flatMap(({ file, source }) => Array.from(source.matchAll(staticCallPattern))
      .map((match) => match[2])
      .filter((key) => !registeredKeys.has(key))
      .map((key) => `${file}: ${key}`))

    expect(missing).toEqual([])
  })

  it('allows only reviewed dynamic template families backed by locale keys', () => {
    const usedTemplates = sources.flatMap(({ file, source }) => Array.from(source.matchAll(templateCallPattern))
      .map((match) => ({ file, template: match[1] })))
    const unknown = usedTemplates.filter(({ template }) => !allowedTemplateCalls.some((allowed) => allowed.template === template))

    expect(unknown).toEqual([])
    allowedTemplateCalls.forEach(({ template, keys }) => {
      expect(usedTemplates.some((usage) => usage.template === template)).toBe(true)
      expect(koKeys.filter((key) => keys.test(key)).length).toBeGreaterThan(0)
    })
  })

  it('allows non-template dynamic calls only at reviewed call sites', () => {
    const unknown = sources.flatMap(({ file, source }) => source.split(/\r?\n/)
      .filter((line) => dynamicCallPattern.test(line))
      .filter((line) => !allowedDynamicCallSites.some((allowed) => allowed.file === file && allowed.line.test(line)))
      .map((line) => `${file}: ${line.trim()}`))

    expect(unknown).toEqual([])
  })
})

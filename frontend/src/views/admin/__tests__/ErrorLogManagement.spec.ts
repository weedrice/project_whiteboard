import { flushPromises, mount, VueWrapper } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ref, nextTick, defineComponent, h } from 'vue'
import { createPrevNextPaginationStub } from '@/test/vue-test-helpers'
import ErrorLogManagement from '../ErrorLogManagement.vue'

// Mock 데이터
const mockErrorLogs = [
    {
        errorLogId: 1,
        errorCode: 'INTERNAL_SERVER_ERROR',
        errorType: 'NullPointerException',
        httpStatus: 500,
        message: 'Cannot invoke method on null',
        requestUri: '/api/v1/posts/1',
        requestMethod: 'GET',
        userId: 10,
        ipAddress: '192.168.1.1',
        userAgent: 'Mozilla/5.0',
        isResolved: 'N',
        resolvedBy: null,
        resolvedAt: null,
        resolvedMemo: null,
        createdAt: '2026-02-23T10:00:00',
        modifiedAt: '2026-02-23T10:00:00'
    },
    {
        errorLogId: 2,
        errorCode: 'BAD_REQUEST',
        errorType: 'BusinessException',
        httpStatus: 400,
        message: 'Invalid parameter',
        requestUri: '/api/v1/users',
        requestMethod: 'POST',
        userId: null,
        ipAddress: '10.0.0.1',
        userAgent: 'Chrome/120',
        isResolved: 'Y',
        resolvedBy: 1,
        resolvedAt: '2026-02-23T12:00:00',
        resolvedMemo: '파라미터 검증 완료',
        createdAt: '2026-02-22T15:00:00',
        modifiedAt: '2026-02-23T12:00:00'
    }
]
const mockErrorLogDetail = {
    ...mockErrorLogs[0],
    stackTrace: 'java.lang.NullPointerException...'
}

const mockStats = {
    totalCount: 100,
    unresolvedCount: 30,
    resolvedCount: 70
}

const mockPageData = {
    content: mockErrorLogs,
    totalPages: 5,
    totalElements: 100,
    number: 0,
    size: 20
}

// Mock 함수
const mockRefetch = vi.fn()
const mockMutateAsync = vi.fn()
const mockFetchErrorLogDetail = vi.fn().mockResolvedValue(mockErrorLogDetail)

const mocks = vi.hoisted(() => {
    const messages: Record<string, string> = {
        'admin.errorLogs.title': '에러 로그 관리',
        'admin.errorLogs.description': '시스템 에러 로그를 관리합니다.',
        'admin.errorLogs.stats.total': '전체',
        'admin.errorLogs.stats.unresolved': '미확인',
        'admin.errorLogs.stats.resolved': '확인 완료',
        'admin.errorLogs.filter.errorType': '에러 타입',
        'admin.errorLogs.filter.httpStatus': 'HTTP 상태',
        'admin.errorLogs.filter.isResolved': '확인 여부',
        'admin.errorLogs.filter.startDate': '시작일',
        'admin.errorLogs.filter.endDate': '종료일',
        'admin.errorLogs.filter.all': '전체',
        'admin.errorLogs.status.unresolved': '미확인',
        'admin.errorLogs.status.resolved': '확인 완료',
        'admin.errorLogs.table.httpStatus': 'HTTP 상태',
        'admin.errorLogs.table.errorCode': '에러 코드',
        'admin.errorLogs.table.errorType': '에러 타입',
        'admin.errorLogs.table.message': '메시지',
        'admin.errorLogs.table.requestUri': '요청 URI',
        'admin.errorLogs.table.ipAddress': 'IP 주소',
        'admin.errorLogs.table.isResolved': '확인 여부',
        'admin.errorLogs.table.createdAt': '생성 일시',
        'admin.errorLogs.table.requestMethod': '요청 메서드',
        'admin.errorLogs.table.userId': '사용자 ID',
        'admin.errorLogs.actions.viewDetail': '상세 보기',
        'admin.errorLogs.actions.resolve': '확인 처리',
        'admin.errorLogs.actions.copy': '복사',
        'admin.errorLogs.detail.title': '에러 로그 상세',
        'admin.errorLogs.detail.errorInfo': '에러 정보',
        'admin.errorLogs.detail.requestInfo': '요청 정보',
        'admin.errorLogs.detail.stackTrace': '스택 트레이스',
        'admin.errorLogs.detail.resolveInfo': '처리 정보',
        'admin.errorLogs.memoPlaceholder': '처리 메모',
        'admin.errorLogs.messages.resolved': '에러 로그를 확인 처리했습니다.',
        'admin.errorLogs.messages.stackTraceCopied': '스택 트레이스를 복사했습니다.',
        'admin.errorLogs.messages.stackTraceCopyFailed': '스택 트레이스 복사에 실패했습니다.',
        'admin.sanction.cancel': '취소'
    }

    return {
        t: (key: string) => messages[key] ?? key,
    }
})

// Mock useI18n
vi.mock('vue-i18n', () => ({
    useI18n: () => ({
        t: mocks.t,
    }),
}))

// Mock toast store
vi.mock('@/stores/toast', () => ({
    useToastStore: () => ({
        addToast: vi.fn(),
    }),
}))

// Mock useAdmin composable
vi.mock('@/composables/useAdmin', () => ({
    useAdmin: () => ({
        useErrorLogs: () => ({
            data: ref(mockPageData),
            isLoading: ref(false),
            refetch: mockRefetch
        }),
        useErrorLog: () => ({
            mutateAsync: mockFetchErrorLogDetail
        }),
        useResolveErrorLog: () => ({
            mutateAsync: mockMutateAsync
        }),
        useErrorLogStats: () => ({
            data: ref(mockStats)
        })
    })
}))

// Stub lucide icons
const iconStub = defineComponent({
    props: { class: String },
    setup(_, { slots }) {
        return () => h('span', { class: 'icon-stub' }, slots.default?.())
    }
})

const PaginationStub = createPrevNextPaginationStub()

describe('ErrorLogManagement', () => {
    let wrapper: VueWrapper

    const mountComponent = () => mount(ErrorLogManagement, {
        global: {
            stubs: {
                Eye: iconStub,
                CheckCircle: iconStub,
                X: iconStub,
                Search: iconStub,
                Copy: iconStub,
                Pagination: PaginationStub,
                Teleport: true
            },
            mocks: {
                $t: mocks.t,
            }
        }
    })

    beforeEach(() => {
        vi.clearAllMocks()
        wrapper = mountComponent()
    })

    describe('헤더', () => {
        it('타이틀과 설명이 표시된다', () => {
            expect(wrapper.text()).toContain('에러 로그 관리')
            expect(wrapper.text()).toContain('시스템 에러 로그를 관리합니다.')
        })
    })

    describe('통계 카드', () => {
        it('전체, 미확인, 확인 완료 통계가 표시된다', () => {
            expect(wrapper.text()).toContain('전체')
            expect(wrapper.text()).toContain('100')
            expect(wrapper.text()).toContain('미확인')
            expect(wrapper.text()).toContain('30')
            expect(wrapper.text()).toContain('확인 완료')
            expect(wrapper.text()).toContain('70')
        })

        it('통계 카드가 3개 렌더링된다', () => {
            const statCards = wrapper.findAll('.error-log-stat-card')
            expect(statCards).toHaveLength(3)
        })
    })

    describe('필터 영역', () => {
        it('필터 입력 요소들이 존재한다', () => {
            const filterItems = wrapper.findAll('.filter-item')
            // 시작일, 종료일, HTTP 상태, 확인 여부, 타입, 액션 = 6개
            expect(filterItems.length).toBeGreaterThanOrEqual(6)
        })

        it('시작일/종료일 기본값이 설정되어 있다', () => {
            const dateInputs = wrapper.findAll('input[type="date"]')
            expect(dateInputs.length).toBe(2)

            // 시작일은 2주 전
            const startValue = (dateInputs[0].element as HTMLInputElement).value
            expect(startValue).toMatch(/^\d{4}-\d{2}-\d{2}$/)

            // 종료일은 오늘
            const endValue = (dateInputs[1].element as HTMLInputElement).value
            expect(endValue).toMatch(/^\d{4}-\d{2}-\d{2}$/)

            // 종료일이 시작일보다 이후여야 함
            expect(new Date(endValue).getTime()).toBeGreaterThanOrEqual(new Date(startValue).getTime())
        })

        it('검색 버튼이 존재한다', () => {
            const searchButton = wrapper.find('.btn-search')
            expect(searchButton.exists()).toBe(true)
            expect(searchButton.text()).toContain('검색')
        })

        it('초기화 버튼이 존재한다', () => {
            const resetButton = wrapper.find('.btn-reset')
            expect(resetButton.exists()).toBe(true)
            expect(resetButton.text()).toContain('초기화')
        })

        it('필터 순서가 올바르다 (시작일 → 종료일 → HTTP 상태 → 확인 여부 → 타입)', () => {
            const filterLabels = wrapper.findAll('.filter-label')
            const labelTexts = filterLabels.map(l => l.text())

            const startIdx = labelTexts.indexOf('시작일')
            const endIdx = labelTexts.indexOf('종료일')
            const httpIdx = labelTexts.indexOf('HTTP 상태')
            const resolvedIdx = labelTexts.indexOf('확인 여부')
            const typeIdx = labelTexts.indexOf('에러 타입')

            expect(startIdx).toBeLessThan(endIdx)
            expect(endIdx).toBeLessThan(httpIdx)
            expect(httpIdx).toBeLessThan(resolvedIdx)
            expect(resolvedIdx).toBeLessThan(typeIdx)
        })

        it('HTTP 상태 셀렉트에 올바른 옵션들이 있다', () => {
            const selects = wrapper.findAll('select')
            const httpSelect = selects[0]
            const options = httpSelect.findAll('option')

            const optionValues = options.map(o => o.text())
            expect(optionValues).toContain('전체')
            expect(optionValues).toContain('400')
            expect(optionValues).toContain('401')
            expect(optionValues).toContain('403')
            expect(optionValues).toContain('404')
            expect(optionValues).toContain('500')
        })

        it('확인 여부 셀렉트에 올바른 옵션들이 있다', () => {
            const selects = wrapper.findAll('select')
            const resolvedSelect = selects[1]
            const options = resolvedSelect.findAll('option')

            const optionValues = options.map(o => o.text())
            expect(optionValues).toContain('전체')
            expect(optionValues).toContain('미확인')
            expect(optionValues).toContain('확인 완료')
        })
    })

    describe('에러 로그 테이블', () => {
        it('테이블 헤더가 올바르게 렌더링된다', () => {
            const headers = wrapper.findAll('th')
            const headerTexts = headers.map(h => h.text())

            expect(headerTexts).toContain('HTTP 상태')
            expect(headerTexts).toContain('에러 코드')
            expect(headerTexts).toContain('에러 타입')
            expect(headerTexts).toContain('메시지')
            expect(headerTexts).toContain('요청 URI')
            expect(headerTexts).toContain('IP 주소')
            expect(headerTexts).toContain('확인 여부')
            expect(headerTexts).toContain('생성 일시')
        })

        it('에러 로그 데이터가 테이블에 표시된다', () => {
            const rows = wrapper.findAll('tbody tr')
            expect(rows).toHaveLength(2)
        })

        it('HTTP 상태 뱃지가 올바른 클래스를 가진다', () => {
            const badges = wrapper.findAll('.http-status-badge')

            // 500 에러 뱃지
            const status500Badge = badges.find(b => b.text() === '500')
            expect(status500Badge?.classes()).toContain('status-500')

            // 400 에러 뱃지
            const status400Badge = badges.find(b => b.text() === '400')
            expect(status400Badge?.classes()).toContain('status-400')
        })

        it('미확인 로그의 확인 여부 뱃지가 올바르게 표시된다', () => {
            const unresolvedBadges = wrapper.findAll('.resolve-badge--unresolved')
            expect(unresolvedBadges.length).toBeGreaterThanOrEqual(1)
        })

        it('확인 완료 로그의 확인 여부 뱃지가 올바르게 표시된다', () => {
            const resolvedBadges = wrapper.findAll('.resolve-badge--resolved')
            expect(resolvedBadges.length).toBeGreaterThanOrEqual(1)
        })

        it('미확인 행에 row-unresolved 클래스가 적용된다', () => {
            const rows = wrapper.findAll('tbody tr')
            const unresolvedRow = rows[0] // errorLogId 1: isResolved='N'
            expect(unresolvedRow.classes()).toContain('row-unresolved')
        })

        it('확인 완료 행에는 row-unresolved 클래스가 적용되지 않는다', () => {
            const rows = wrapper.findAll('tbody tr')
            const resolvedRow = rows[1] // errorLogId 2: isResolved='Y'
            expect(resolvedRow.classes()).not.toContain('row-unresolved')
        })
    })

    describe('액션 버튼', () => {
        it('모든 행에 상세 보기 버튼이 있다', () => {
            const detailButtons = wrapper.findAll('.btn-icon')
            expect(detailButtons.length).toBeGreaterThanOrEqual(2)
        })

        it('아이콘 전용 버튼에 접근 가능한 이름과 button 타입이 있다', () => {
            const detailButton = wrapper.get('.btn-icon')
            const resolveButton = wrapper.get('.btn-icon--resolve')

            expect(detailButton.attributes('type')).toBe('button')
            expect(detailButton.attributes('aria-label')).toBe('상세 보기')
            expect(detailButton.get('svg').attributes('aria-hidden')).toBe('true')
            expect(resolveButton.attributes('type')).toBe('button')
            expect(resolveButton.attributes('aria-label')).toBe('확인 처리')
        })

        it('상세 보기 버튼 클릭 시 상세 API 결과의 스택 트레이스를 모달에 표시한다', async () => {
            const detailButtons = wrapper.findAll('.btn-icon')

            await detailButtons[0].trigger('click')
            await flushPromises()
            await nextTick()

            expect(mockFetchErrorLogDetail).toHaveBeenCalledWith(1)
            expect(wrapper.text()).toContain('java.lang.NullPointerException...')
            expect(wrapper.find('.btn-copy-stack-trace').exists()).toBe(true)
            expect(wrapper.get('.btn-close').attributes('type')).toBe('button')
            expect(wrapper.get('.btn-close').attributes('aria-label')).toBe('상세 모달 닫기')
        })

        it('미확인 행에만 확인 처리 버튼이 있다', () => {
            const resolveButtons = wrapper.findAll('.btn-icon--resolve')
            expect(resolveButtons).toHaveLength(1) // errorLogId 1만 미확인
        })

        it('확인 처리 모달 닫기 버튼에 접근 가능한 이름이 있다', async () => {
            await wrapper.get('.btn-icon--resolve').trigger('click')
            await nextTick()

            expect(wrapper.get('.btn-close').attributes('type')).toBe('button')
            expect(wrapper.get('.btn-close').attributes('aria-label')).toBe('확인 처리 모달 닫기')
        })
    })

    describe('페이지네이션', () => {
        it('페이지 정보가 표시된다', () => {
            expect(wrapper.text()).toContain('100')
            expect(wrapper.find('[data-test="pagination"]').exists()).toBe(true)
        })

        it('이전/다음 페이지 버튼이 존재한다', () => {
            const pageButtons = wrapper.findAll('[data-test$="-page"]')
            expect(pageButtons).toHaveLength(2)
        })

        it('첫 페이지에서 이전 버튼이 비활성화된다', () => {
            const prevButton = wrapper.get('[data-test="prev-page"]')
            expect((prevButton.element as HTMLButtonElement).disabled).toBe(true)
        })

        it('페이지 이동 버튼에 접근 가능한 이름이 있다', () => {
            const prevButton = wrapper.get('[data-test="prev-page"]')
            const nextButton = wrapper.get('[data-test="next-page"]')

            expect(prevButton.attributes('type')).toBe('button')
            expect(nextButton.attributes('type')).toBe('button')
        })
    })

    describe('필터 상호작용', () => {
        it('초기화 버튼 클릭 시 필터 값이 기본값으로 돌아간다', async () => {
            // 에러 타입 변경
            const typeInput = wrapper.find('input[type="text"]')
            await typeInput.setValue('TestException')
            expect((typeInput.element as HTMLInputElement).value).toBe('TestException')

            // 초기화
            const resetButton = wrapper.find('.btn-reset')
            await resetButton.trigger('click')
            await nextTick()

            // 에러 타입이 초기화됨
            expect((typeInput.element as HTMLInputElement).value).toBe('')

            // 날짜 필드는 기본값으로 복원됨
            const dateInputs = wrapper.findAll('input[type="date"]')
            const startValue = (dateInputs[0].element as HTMLInputElement).value
            const endValue = (dateInputs[1].element as HTMLInputElement).value
            expect(startValue).toMatch(/^\d{4}-\d{2}-\d{2}$/)
            expect(endValue).toMatch(/^\d{4}-\d{2}-\d{2}$/)
        })
    })
})

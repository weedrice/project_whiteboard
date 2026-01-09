# Frontend 개선 사항 Phase 3

Phase 2까지 완료한 개선 사항 이후 추가로 개선할 수 있는 부분들을 정리한 문서입니다.

---

## 🔴 중요 (Critical) - 타입 안정성

### 1. **any 타입 제거**

**위치:** 여러 파일

**문제점:**
- `any` 타입 사용으로 타입 안정성 저하
- 런타임 에러 가능성 증가

**개선 대상:**
- `frontend/src/api/index.ts` (line 40): `reject: (error: any) => void`
- `frontend/src/components/common/ui/BaseInput.vue` (line 2): `:style="$attrs.style as any"`
- `frontend/src/composables/useNotification.ts` (line 101): `(oldData: any) =>`
- `frontend/src/components/user/ProfileEditor.vue` (line 273): `catch (error: any)`
- `frontend/src/components/user/UserNavigation.vue` (line 7): `(el as any).$el`

**개선 방안:**
```typescript
// api/index.ts
interface FailedRequest {
    resolve: (token: string | null) => void
    reject: (error: unknown) => void  // any → unknown
}

// BaseInput.vue
:style="$attrs.style as CSSProperties"  // any → CSSProperties

// useNotification.ts
queryClient.setQueriesData({ queryKey: ['notifications'] }, (oldData: PageResponse<Notification> | undefined) => {
    // ...
})

// ProfileEditor.vue
} catch (error: unknown) {  // any → unknown
    // ...
}

// UserNavigation.vue
:ref="el => { if (el) tabRefs[index] = (el as ComponentPublicInstance).$el }"
```

---

### 2. **타입이 없는 ref/reactive 초기화**

**위치:** 여러 파일

**문제점:**
- 타입 추론이 어려워 타입 안정성 저하
- IDE 자동완성 미지원

**개선 대상:**
- `frontend/src/views/home/HomeFeed.vue`: `const posts = ref([])`
- `frontend/src/views/user/SubscribedBoards.vue`: `const boards = ref([])`
- `frontend/src/views/user/MyPageDashboard.vue`: `const profile = ref(null)`

**개선 방안:**
```typescript
// HomeFeed.vue
import type { PostSummary } from '@/types'
const posts = ref<PostSummary[]>([])

// SubscribedBoards.vue
import type { Board } from '@/types'
const boards = ref<Board[]>([])

// MyPageDashboard.vue
import type { User } from '@/types/user'
const profile = ref<User | null>(null)
```

---

## 🟡 중요 (Important) - 코드 품질 및 성능

### 3. **날짜 포맷팅 최적화**

**위치:** `frontend/src/utils/date.ts`

**문제점:**
- 매번 `new Date()` 생성으로 인한 성능 저하
- 날짜 파싱 결과를 캐싱하지 않음
- 상대 시간 계산이 매번 수행됨

**개선 방안:**
```typescript
// 날짜 포맷팅 결과 캐싱
const dateCache = new Map<string, string>()

export function formatDate(dateString: string | number[]): string {
    if (!dateString) return ''
    
    const cacheKey = Array.isArray(dateString) 
        ? dateString.join(',') 
        : dateString
    
    if (dateCache.has(cacheKey)) {
        return dateCache.get(cacheKey)!
    }
    
    let result: string
    if (Array.isArray(dateString)) {
        const [year, month, day, hour, minute, second] = dateString
        const utcDate = new Date(Date.UTC(year, month - 1, day, hour, minute, second || 0))
        result = utcDate.toLocaleString()
    } else {
        result = new Date(dateString).toLocaleString()
    }
    
    // 캐시 크기 제한 (메모리 누수 방지)
    if (dateCache.size > 1000) {
        const firstKey = dateCache.keys().next().value
        dateCache.delete(firstKey)
    }
    
    dateCache.set(cacheKey, result)
    return result
}
```

또는 더 나은 방법으로 `Intl.DateTimeFormat` 사용:
```typescript
const dateFormatter = new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
})

export function formatDate(dateString: string | number[]): string {
    if (!dateString) return ''
    
    const date = Array.isArray(dateString)
        ? new Date(Date.UTC(dateString[0], dateString[1] - 1, dateString[2], dateString[3], dateString[4], dateString[5] || 0))
        : new Date(dateString)
    
    return dateFormatter.format(date)
}
```

---

### 4. **주석 처리된 코드 제거**

**위치:** 여러 파일

**문제점:**
- 주석 처리된 코드가 남아있어 코드베이스 복잡도 증가
- 유지보수 어려움

**개선 대상:**
- `frontend/src/utils/storage.ts`: 주석 처리된 console.log 예시
- `frontend/src/components/common/widgets/AdBanner.vue`: 주석 처리된 console.error

**개선 방안:**
- 주석 처리된 코드 제거
- 필요시 문서화로 대체

---

### 5. **에러 처리 일관성 개선**

**위치:** 여러 파일

**문제점:**
- 일부 파일에서 에러 처리가 일관되지 않음
- `useErrorHandler` composable을 사용하지 않는 곳이 있음

**개선 방안:**
- 모든 에러 처리에서 `useErrorHandler` 사용
- 에러 처리 패턴 표준화

---

### 6. **Computed 속성 최적화**

**위치:** 여러 컴포넌트

**문제점:**
- 복잡한 computed 속성이 매번 재계산됨
- 불필요한 의존성으로 인한 재계산

**개선 방안:**
- `computed` 대신 `shallowRef` + `watch` 사용 (복잡한 계산의 경우)
- 의존성 최소화
- 메모이제이션 적용

---

## 🟢 권장 (Recommended) - 사용자 경험 및 접근성

### 7. **로딩 상태 개선**

**위치:** 여러 컴포넌트

**문제점:**
- 로딩 상태가 일관되지 않음
- 일부 컴포넌트에서 로딩 인디케이터가 없음

**개선 방안:**
- 전역 로딩 인디케이터 추가
- 로딩 상태 표준화

---

### 8. **키보드 네비게이션 개선**

**위치:** 여러 컴포넌트

**문제점:**
- 일부 컴포넌트에서 키보드 네비게이션이 완전하지 않음
- 포커스 관리가 일관되지 않음

**개선 방안:**
- 모든 인터랙티브 요소에 키보드 접근성 추가
- 포커스 트랩 및 포커스 복원 개선

---

### 9. **폼 제출 최적화**

**위치:** 여러 폼 컴포넌트

**문제점:**
- 중복 제출 방지가 일관되지 않음
- 제출 중 상태 표시가 일관되지 않음

**개선 방안:**
- 전역 폼 제출 상태 관리
- 중복 제출 방지 유틸리티 생성

---

### 10. **이미지 로딩 최적화**

**위치:** 여러 컴포넌트

**문제점:**
- 이미지 로딩 실패 시 처리 불일치
- 이미지 최적화 유틸리티가 실제로 사용되지 않음

**개선 방안:**
- `getOptimizedImageUrl` 함수 실제 적용
- 이미지 에러 핸들링 표준화
- placeholder 이미지 통일

---

### 11. **디바운싱/스로틀링 개선**

**위치:** 여러 컴포넌트

**문제점:**
- 검색, 스크롤 등에서 디바운싱이 일관되지 않음
- 상수 파일의 DEBOUNCE_DELAY가 실제로 사용되지 않음

**개선 방안:**
- 공통 디바운싱 composable 생성
- 상수 파일의 값 실제 적용

---

### 12. **테스트 커버리지 개선**

**위치:** 전체

**문제점:**
- 테스트 커버리지가 낮을 가능성
- 중요한 비즈니스 로직에 대한 테스트 부족

**개선 방안:**
- 유틸리티 함수 테스트 추가
- Composable 테스트 추가
- 컴포넌트 테스트 추가

---

## 📋 우선순위별 실행 계획

### Phase 3-1 (즉시) - 타입 안정성
1. ⏸️ **any 타입 제거**
2. ⏸️ **타입이 없는 ref/reactive 초기화 개선**

### Phase 3-2 (단기 - 1주) - 코드 품질
3. ⏸️ **날짜 포맷팅 최적화**
4. ⏸️ **주석 처리된 코드 제거**
5. ⏸️ **에러 처리 일관성 개선**

### Phase 3-3 (중기 - 2-4주) - 성능 및 UX
6. ⏸️ **Computed 속성 최적화**
7. ⏸️ **로딩 상태 개선**
8. ⏸️ **키보드 네비게이션 개선**
9. ⏸️ **폼 제출 최적화**
10. ⏸️ **이미지 로딩 최적화**
11. ⏸️ **디바운싱/스로틀링 개선**
12. ⏸️ **테스트 커버리지 개선**

---

## 참고 자료

- [TypeScript Best Practices](https://www.typescriptlang.org/docs/handbook/declaration-files/do-s-and-don-ts.html)
- [Vue 3 Performance Optimization](https://vuejs.org/guide/best-practices/performance.html)
- [Web Accessibility Guidelines](https://www.w3.org/WAI/WCAG21/quickref/)
- [Intl.DateTimeFormat](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Intl/DateTimeFormat)

---

**작성일:** 2025-01-09  
**분석 기준:** 프론트엔드 전체 코드베이스 리뷰 (Phase 2 완료 후)

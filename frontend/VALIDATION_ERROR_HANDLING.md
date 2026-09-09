# Validation 에러 처리 가이드

백엔드에서 개선된 Validation 에러 응답 구조를 활용하는 방법을 설명합니다.

## 개요

HTTP 400 / `C008` 검증 오류 중 필드를 식별할 수 있는 경우 `error.details`에 필드별 메시지 배열을 반환합니다. JSON 본문 파싱 실패처럼 필드를 특정할 수 없는 경우에는 `details` 없이 일반 메시지를 반환하므로 항상 유무와 형태를 확인합니다. 다음은 한국어 응답의 구조 예시이며 실제 필드와 문구는 검증 규칙 및 `Accept-Language`에 따라 달라집니다:

```json
{
  "success": false,
  "error": {
    "code": "C008",
    "message": "2개 필드에 대해 유효성 검사에 실패했습니다.",
    "details": {
      "email": ["이메일은 필수입니다.", "이메일 형식이 올바르지 않습니다."],
      "password": ["비밀번호는 8자 이상이어야 합니다."]
    }
  }
}
```

## 타입 정의

### ErrorResponse
```typescript
export interface ErrorResponse {
    code: string
    message: string
    details?: ValidationErrors | Record<string, unknown>
}
```

### ValidationErrors
```typescript
export type ValidationErrors = Record<string, string[]>
```

## 유틸리티 함수 사용법

### 1. Validation 에러 추출

```typescript
import { extractValidationErrors } from '@/utils/errorHandler'
import type { AxiosError } from 'axios'

try {
  await someApiCall()
} catch (error) {
  const axiosError = error as AxiosError
  const validationErrors = extractValidationErrors(axiosError)
  
  if (validationErrors) {
    // Validation 에러 처리
    console.log(validationErrors) // { email: ["에러1", "에러2"], password: ["에러3"] }
  }
}
```

### 2. 특정 필드의 에러 가져오기

```typescript
import { extractValidationErrors, getFieldError } from '@/utils/errorHandler'

const validationErrors = extractValidationErrors(axiosError)
const emailError = getFieldError(validationErrors, 'email') // 첫 번째 에러 메시지 반환
```

### 3. 에러 메시지 추출

```typescript
import { extractErrorMessage } from '@/utils/errorHandler'

const errorMessage = extractErrorMessage(axiosError) // 일반 에러 메시지 반환
```

### 4. 모든 Validation 에러 합치기

```typescript
import { extractValidationErrors, combineValidationErrors } from '@/utils/errorHandler'

const validationErrors = extractValidationErrors(axiosError)
const allErrors = combineValidationErrors(validationErrors, ', ') 
// "에러1, 에러2, 에러3"
```

## 공통 오류 처리와 중복 토스트 방지

공유 API 인터셉터는 기본적으로 HTTP 400 검증 오류의 첫 필드 메시지를 토스트로 표시합니다. 처리된 오류에는 `suppressGlobalErrorToast` 표시를 붙여 `queryClient.ts`의 전역 Query/Mutation 오류 처리에서 재표시하지 않습니다. 필드 아래에 오류를 표시하는 로직은 유지하되 컴포넌트에서 같은 토스트를 다시 추가하지 않습니다.

직접 토스트를 표시할 경우 `shouldSuppressGlobalErrorToast(error)`를 먼저 확인합니다. 폼이 오류 표시를 전담하도록 API 요청에 `skipGlobalErrorHandler: true`를 사용하는 경우, Vue Query의 전역 오류까지 끄려면 해당 query/mutation에 `meta: { errorMessage: false }`도 설정해야 합니다. 두 옵션은 서로 다른 계층에 적용됩니다. 인증 API 일부는 이미 공통 API 토스트를 건너뛰므로 해당 폼의 기존 오류 처리 방식을 따릅니다.

## 컴포넌트에서 사용 예시

아래 `submitForm`은 공유 API 레이어를 호출하는 해당 기능의 함수라고 가정합니다.

### 예시 1: 폼 컴포넌트에서 필드별 에러 표시

```vue
<template>
  <form @submit.prevent="handleSubmit">
    <BaseInput 
      v-model="form.email" 
      :error="errors.email"
      label="이메일"
    />
    <BaseInput 
      v-model="form.password" 
      :error="errors.password"
      label="비밀번호"
    />
    <BaseButton type="submit">제출</BaseButton>
  </form>
</template>

<script setup lang="ts">
import { reactive } from 'vue'
import { extractValidationErrors, getFieldError } from '@/utils/errorHandler'
import type { AxiosError } from 'axios'

const errors = reactive<Record<string, string>>({})
const form = reactive({
  email: '',
  password: ''
})

const handleSubmit = async () => {
  try {
    await submitForm(form)
  } catch (error) {
    const axiosError = error as AxiosError
    const validationErrors = extractValidationErrors(axiosError)
    
    if (validationErrors) {
      // 필드별 에러 설정
      errors.email = getFieldError(validationErrors, 'email') || ''
      errors.password = getFieldError(validationErrors, 'password') || ''
    }
  }
}
</script>
```

### 예시 2: TanStack Query와 함께 사용

```typescript
import { reactive } from 'vue'
import { useMutation } from '@tanstack/vue-query'
import { extractValidationErrors, getFieldError } from '@/utils/errorHandler'
import type { AxiosError } from 'axios'

const errors = reactive<Record<string, string>>({})

const { mutate } = useMutation({
  mutationFn: submitForm,
  onError: (error: Error) => {
    const axiosError = error as AxiosError
    const validationErrors = extractValidationErrors(axiosError)
    
    if (validationErrors) {
      // 모든 필드 에러 설정
      Object.keys(validationErrors).forEach(field => {
        errors[field] = getFieldError(validationErrors, field) || ''
      })
    }
  }
})
```

### 예시 3: 여러 필드 에러를 토스트로 표시

```typescript
import { extractValidationErrors, combineValidationErrors, shouldSuppressGlobalErrorToast } from '@/utils/errorHandler'
import { useToastStore } from '@/stores/toast'
import type { AxiosError } from 'axios'

const toastStore = useToastStore()

try {
  await submitForm()
} catch (error) {
  const axiosError = error as AxiosError
  const validationErrors = extractValidationErrors(axiosError)
  
  if (validationErrors && !shouldSuppressGlobalErrorToast(error)) {
    // 공통 처리에서 표시하지 않은 경우에만 직접 토스트 표시
    const allErrors = combineValidationErrors(validationErrors, '\n')
    toastStore.addToast(allErrors, 'error')
  }
}
```

## 기존 코드 마이그레이션

### Before (기존 방식)
```typescript
catch (error: any) {
  if (error.response?.data?.message) {
    errors.displayName = error.response.data.message
  }
}
```

### After (개선된 방식)
```typescript
import { extractValidationErrors, getFieldError, extractErrorMessage, shouldSuppressGlobalErrorToast } from '@/utils/errorHandler'
import type { AxiosError } from 'axios'

catch (error) {
  const axiosError = error as AxiosError
  const validationErrors = extractValidationErrors(axiosError)
  
  if (validationErrors) {
    // Validation 에러: 필드별로 처리
    errors.displayName = getFieldError(validationErrors, 'displayName') || ''
  } else if (!shouldSuppressGlobalErrorToast(error)) {
    // 공통 처리에서 아직 표시하지 않은 일반 오류만 표시
    const errorMessage = extractErrorMessage(axiosError)
    toastStore.addToast(errorMessage, 'error')
  }
}
```

## 참고사항

1. **하위 호환성**: 기존 에러 응답 구조도 계속 지원됩니다. `details` 필드가 없으면 일반 에러로 처리됩니다.

2. **필드명 매핑**: 백엔드에서 반환하는 필드명과 프론트엔드 폼 필드명이 다를 수 있습니다. 필요시 매핑 로직을 추가하세요.

3. **에러 우선순위**: 필드 표시에는 `extractValidationErrors`로 검사한 `details`와 `getFieldError`를 사용합니다. `extractErrorMessage` 자체는 `details`를 합치지 않고 일반 `message`를 추출합니다. 배열 값이 모두 문자열 배열인 객체만 ValidationErrors로 인정하므로, 다른 비즈니스 오류의 `details`를 필드 오류로 가정하지 않습니다.

## 관련 파일

- `frontend/src/types/common.ts` - 타입 정의
- `frontend/src/utils/errorHandler.ts` - 유틸리티 함수
- `frontend/src/api/index.ts` - 공유 Axios 인스턴스와 인터셉터 등록
- `frontend/src/api/apiResponseErrorHandler.ts` - 응답 오류 흐름과 처리 완료 표시
- `frontend/src/api/errorHandling.ts` - HTTP 상태별 자동 토스트 처리
- `frontend/src/queryClient.ts` - Query/Mutation 전역 오류 처리와 중복 억제
- `backend/src/main/java/com/weedrice/whiteboard/global/exception/GlobalExceptionHandler.java` - 검증 응답 생성
- `frontend/src/composables/useErrorHandler.ts` - Validation 유틸리티를 감싼 공용 composable

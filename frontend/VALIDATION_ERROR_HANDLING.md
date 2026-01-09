# Validation 에러 처리 가이드

백엔드에서 개선된 Validation 에러 응답 구조를 활용하는 방법을 설명합니다.

## 개요

백엔드에서 Validation 에러가 발생하면, 이제 모든 필드의 에러를 한 번에 반환합니다:

```json
{
  "success": false,
  "error": {
    "code": "C008",
    "message": "Validation failed for 2 field(s)",
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
    details?: ValidationErrors | any
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

## 컴포넌트에서 사용 예시

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
import { extractValidationErrors, combineValidationErrors } from '@/utils/errorHandler'
import { useToastStore } from '@/stores/toast'

const toastStore = useToastStore()

try {
  await submitForm()
} catch (error) {
  const axiosError = error as AxiosError
  const validationErrors = extractValidationErrors(axiosError)
  
  if (validationErrors) {
    // 모든 에러를 하나의 메시지로 합쳐서 표시
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
import { extractValidationErrors, getFieldError, extractErrorMessage } from '@/utils/errorHandler'
import type { AxiosError } from 'axios'

catch (error) {
  const axiosError = error as AxiosError
  const validationErrors = extractValidationErrors(axiosError)
  
  if (validationErrors) {
    // Validation 에러: 필드별로 처리
    errors.displayName = getFieldError(validationErrors, 'displayName') || ''
  } else {
    // 일반 에러: 메시지만 표시
    const errorMessage = extractErrorMessage(axiosError)
    toastStore.addToast(errorMessage, 'error')
  }
}
```

## 참고사항

1. **하위 호환성**: 기존 에러 응답 구조도 계속 지원됩니다. `details` 필드가 없으면 일반 에러로 처리됩니다.

2. **필드명 매핑**: 백엔드에서 반환하는 필드명과 프론트엔드 폼 필드명이 다를 수 있습니다. 필요시 매핑 로직을 추가하세요.

3. **에러 우선순위**: Validation 에러가 있으면 `details`를 우선적으로 사용하고, 없으면 `message`를 사용합니다.

## 관련 파일

- `frontend/src/types/common.ts` - 타입 정의
- `frontend/src/utils/errorHandler.ts` - 유틸리티 함수
- `frontend/src/api/index.ts` - API 인터셉터 (자동 처리)
- `frontend/src/components/user/ProfileEditor.vue` - 사용 예시

/**
 * 손으로 유지하는 응답 타입이 백엔드 스키마와 어긋나지 않는지 **타입 수준에서** 확인한다.
 *
 * A6의 문제 제기는 "백엔드 DTO를 수정해도 프론트 타입은 아무 신호를 주지 않는다"였다.
 * 생성 타입을 도입하되 51개 API 파일을 한 번에 갈아엎는 대신, 생성 타입을 **기준자**로
 * 삼아 수기 타입을 검증한다. 백엔드가 필드 이름이나 타입을 바꾸면 `npm run type-check`가
 * 깨진다. CI가 이미 그 명령을 돌리므로 별도 실행 장치가 필요 없다.
 *
 * 이 파일은 런타임 코드를 내보내지 않는다. 타입 검사만이 목적이다.
 *
 * <p>검사 방향은 "wire 값을 수기 타입의 대응 필드에 넣을 수 있는가"다. 수기 타입이 wire의
 * 일부만 골라 쓰는 것은 정상이므로 완전 일치는 요구하지 않는다. 이름이 어긋나거나 타입이
 * 맞지 않을 때만 실패한다.
 */

import type { components } from '@/types/generated/api'
import type { Comment } from '@/types/comment'
import type { BoardListItem, Post, PostSummary } from '@/types/board'
import type { AdminShopItem } from '@/types/admin'

type Schemas = components['schemas']

/**
 * 비교 대상은 **스칼라 필드로 한정한다.**
 *
 * 중첩 DTO(`category`, `board`, `author`, `poll`, `children`, `mentions` ...)는 정규화
 * 계층이 wire 모양과 내부 모양을 의도적으로 다르게 잡는다(`CategoryInfo` ->
 * `PostCategorySummary` 등). 그 매핑은 normalizer가 소유하므로 여기서 구조 일치를
 * 요구하면 정상 설계가 오류로 잡힌다. 목록으로 하나씩 빼는 대신 규칙으로 잘라낸다.
 *
 * **이 검사가 잡는 것과 못 잡는 것**을 분명히 해 둔다.
 * - 잡는다: 공유 필드의 **타입** 어긋남(백엔드가 `number`를 `string`으로 바꾸는 등).
 * - 못 잡는다: **이름 변경**. 이름이 바뀌면 그 필드가 공유 집합에서 빠질 뿐 오류가 나지 않는다.
 *
 * 이름 변경은 아래 명시적 단언과 `booleanWireNameContract.spec.ts`(백엔드 소스를 직접 읽어
 * wire 이름을 계산한다)가 맡는다. 이 파일 하나로 다 잡힌다고 믿으면 안 된다.
 */
type Scalar = string | number | boolean
type ScalarKeys<W> = {
    [K in keyof W]-?: NonNullable<W[K]> extends Scalar | readonly Scalar[] ? K : never
}[keyof W]

/** 두 타입이 공유하는 키만 뽑아 비교 대상을 맞춘다. */
type SharedKeys<W, T> = Extract<ScalarKeys<W>, keyof T>

/**
 * 공통 필드의 **값 타입**이 서로 맞아야 한다.
 *
 * optional 여부는 비교에서 뺀다(`Required`). springdoc은 `@NotNull`이 없는 필드를 전부
 * optional로 내보내므로, 그대로 비교하면 수기 타입이 필수로 선언한 필드가 전부 걸려
 * 신호가 잡음에 묻힌다. 이름 어긋남과 타입 어긋남만 잡는 것이 이 검사의 목적이다.
 */
type WireAssignableToHand<W, T> =
    Required<Pick<W, SharedKeys<W, T>>> extends Required<Pick<T, SharedKeys<W, T>>> ? true : false

/** `true`가 아니면 컴파일 오류. 실패 시 어느 줄인지 바로 드러난다. */
type Assert<T extends true> = T

/**
 * 공통 키가 있는지도 본다. 이름이 통째로 어긋나면 공통 키가 0개가 되고,
 * `Pick<W, never> extends Pick<T, never>`는 항상 참이라 위 검사가 공허하게 통과한다.
 */
type HasSharedKeys<W, T> = [SharedKeys<W, T>] extends [never] ? false : true

type _PostResponseShared = Assert<HasSharedKeys<Schemas['PostResponse'], Post>>
type _PostResponse = Assert<WireAssignableToHand<Schemas['PostResponse'], Post>>

type _PostSummaryShared = Assert<HasSharedKeys<Schemas['PostSummary'], PostSummary>>
type _PostSummary = Assert<WireAssignableToHand<Schemas['PostSummary'], PostSummary>>

type _CommentShared = Assert<HasSharedKeys<Schemas['CommentResponse'], Comment>>
type _Comment = Assert<WireAssignableToHand<Schemas['CommentResponse'], Comment>>

type _BoardListShared = Assert<HasSharedKeys<Schemas['BoardListResponse'], BoardListItem>>
type _BoardList = Assert<WireAssignableToHand<Schemas['BoardListResponse'], BoardListItem>>

type _AdminShopItemShared = Assert<HasSharedKeys<Schemas['AdminShopItemResponse'], AdminShopItem>>
type _AdminShopItem = Assert<WireAssignableToHand<Schemas['AdminShopItemResponse'], AdminShopItem>>

/**
 * A8이 정리한 boolean 키가 스키마에 그대로 있는지 본다.
 * 위 구조 검사는 이름 변경을 못 잡으므로, 중요한 키는 여기서 이름을 직접 못 박는다.
 */
type _A8PostNotice = Assert<Schemas['PostResponse'] extends { isNotice?: boolean } ? true : false>
type _A8PostLiked = Assert<Schemas['PostResponse'] extends { isLiked?: boolean } ? true : false>
type _A8CommentDeleted = Assert<Schemas['CommentResponse'] extends { isDeleted?: boolean } ? true : false>
type _A8BoardActive = Assert<Schemas['BoardListResponse'] extends { isActive?: boolean } ? true : false>
type _AdminShopSaleFlags = Assert<
    Schemas['AdminShopItemResponse'] extends {
        isActive?: boolean
        isSaleEnabled?: boolean
        purchasable?: boolean
    } ? true : false
>
type _AdminShopSaleStatusRequest = Assert<
    Schemas['AdminShopItemSaleStatusRequest'] extends {
        saleEnabled: boolean
        reason: string
    } ? true : false
>

/** 접두사가 떨어진 legacy 필드는 이번 정리 대상이 아니므로 그대로여야 한다. */
type _LegacySummaryNotice = Assert<Schemas['PostSummary'] extends { notice?: boolean } ? true : false>
type _LegacySummaryLiked = Assert<Schemas['PostSummary'] extends { liked?: boolean } ? true : false>

/**
 * 제외 도메인 스키마가 새로 새어 들어오지 않았는지 본다.
 *
 * 아래 세 개는 `AgentController`가 아니라 `UserController`의 사용자 대면 엔드포인트
 * (`/api/v1/users/me/agents` 계열)가 서빙하므로 생성 대상이 맞다. 프론트도
 * `userAgentApi.ts`에서 실제로 소비한다. 나머지 `Agent*` 스키마가 들어오면
 * `/api/v1/agents/**` 제외가 풀린 것이므로 여기서 걸린다.
 */
type ExpectedAgentSchemas = 'AgentClaimRequest' | 'AgentResponse' | 'AgentListResponse'
/**
 * `ad` 도메인은 접두사로 거를 수 없다. `Admin*` 스키마가 다수 존재해 `Ad${string}`이
 * 그것들과 충돌한다. ad 경로 제외는 `OpenApiSpecSnapshotTest`가 경로 기준으로 확인한다.
 */
type _NoUnexpectedAgentSchemas = Assert<
    [Exclude<Extract<keyof Schemas, `Agent${string}`>, ExpectedAgentSchemas>] extends [never]
        ? true
        : false
>

export {}




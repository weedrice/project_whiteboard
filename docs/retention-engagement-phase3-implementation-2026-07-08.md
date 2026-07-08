# NoviIs retention and engagement phase 3 implementation

Date: 2026-07-08

## Feature status

| Item | Status | Notes |
| --- | --- | --- |
| N1 resume reading | Applied | `PostResponse.lastViewedAt` is read from the existing view history before the detail read updates history. Comment divider is limited to oldest-first order; other sorts keep only new-comment highlight. |
| N2 web push foundation | Partially applied | `push_subscriptions` and user setting fields were added in `V30`. Subscription CRUD and a custom push service worker exist. Actual VAPID send/410 cleanup is deferred until the web-push dependency is approved. |
| N3 onboarding foundation | Partially applied | Board recommendation API and onboarding completion API exist. Frontend API clients are ready; the signup redirect/banner UI remains follow-up work. |
| N4 keyword notifications | Partially applied | `NotificationType.KEYWORD`, DB constraints, user settings, API, frontend presentation, and title exact/partial matching notification publishing were added. Private/secret posts are excluded; keyword management UI and block-relationship filtering remain follow-up. |
| N5 poll attachment | Backend/API applied | `V32` adds polls, options, and votes. Posts can be created with one attached poll, and vote/delete-vote APIs are available. Editor/vote UI remains follow-up work. |
| N6 semantic search labeling | Applied | Similarity percentage is hidden; semantic results are labeled as recommendation copy and promoted when keyword results are empty. |
| N7 point visibility | Applied | Post/comment create responses include `earnedPoints`, the frontend shows a polite success toast, invalidates user point balance, and point history now separates earn/spend rows with balance-after visibility. |

## Public API

### Posts and comments

- `PostResponse.lastViewedAt: string | null`
- `PostResponse.lastReadCommentId` remains unchanged.
- `POST /api/v1/posts` returns `{ postId, earnedPoints? }`.
- Comment creation returns `{ commentId, earnedPoints? }`.
- Post creation accepts optional `poll`.

### Push and onboarding

- `POST /api/v1/users/me/push-subscriptions`
- `DELETE /api/v1/users/me/push-subscriptions`
- Push payload: `endpoint`, `keys.p256dh`, `keys.auth`, `userAgent`
- `GET /api/v1/boards/recommendations?topics=...`
- `PUT /api/v1/users/me/onboarding-complete`

### Keyword subscriptions

- `GET /api/v1/users/me/keyword-subscriptions`
- `POST /api/v1/users/me/keyword-subscriptions`
- `DELETE /api/v1/users/me/keyword-subscriptions/{subscriptionId}`
- Frontend API clients exist, but there is not yet a reachable keyword management screen.

### Polls

- `POST /api/v1/posts/{postId}/poll/vote`
- `DELETE /api/v1/posts/{postId}/poll/vote`
- Vote payload: `optionIds: number[]`
- V1 supports one poll per post, 2 to 10 options, close time, anonymous flag, and single/multiple choice flag.

## Database

- `V30__push_subscriptions_and_onboarding.sql`
  - Adds `user_settings.push_enabled`.
  - Adds `user_settings.onboarding_completed_at`.
  - Creates `push_subscriptions`.
- `V31__keyword_subscriptions.sql`
  - Extends notification type constraints for `KEYWORD`.
  - Creates `user_keyword_subscriptions`.
- `V32__post_polls.sql`
  - Creates `polls`, `poll_options`, and `poll_votes`.
- `V33__allow_multiple_poll_votes.sql`
  - Replaces the single-vote table constraint with `(poll_id, user_id, option_id)` uniqueness so multiple-choice polls can store multiple selected options.
  - Vote mutations read the poll row with a pessimistic write lock before delete-and-insert so concurrent single-choice vote changes remain serialized at the service layer.

## Verification notes

- Frontend lint passed after each implementation phase.
- Targeted Vitest coverage was run for post/comment point rewards, post detail/comment divider behavior, and search labeling.
- Targeted backend tests were run for post/comment controllers, post services, user settings/controllers, board recommendations, keyword compile path, and poll controller wiring.
- Full type-check remains intentionally deferred because the project already has known lowlight, Tiptap, and PWA virtual module resolution issues.

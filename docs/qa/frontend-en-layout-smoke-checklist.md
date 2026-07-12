# Frontend English Layout QA Checklist

## Policy

- User-generated names and titles may truncate when the full value remains available through context or a title.
- Action labels, navigation labels, filter chips, badges, and segmented controls must not silently truncate.
- Chips and pill segmented controls size to content and wrap inside their container.
- Joined segmented controls use the available mobile width and allow labels to wrap; desktop width remains content-based.
- Mobile bottom navigation uses short locale-specific labels and permits at most two lines.
- Responsive rows must use `min-width: 0` on flexible text children and wrap actions before they overflow.

## Viewports

- 360 x 800: narrow Android-sized mobile.
- 390 x 844: common modern mobile.
- 768 x 1024: tablet.
- 1280 x 800: desktop.

Run the pass in English in both light and dark themes. Check horizontal overflow, clipped focus rings, overlapping actions,
unexpected one-character columns, inaccessible truncated labels, and layout shifts after data loads.

## Screen Pass

1. Home feed: attendance panel, feed segments, activity cards, and board links.
2. All spaces: search, sort/filter controls, and space cards.
3. Space detail: header actions, density control, notices, and post list.
4. Post detail: metadata badges, poll card, comment sort, and mobile actions.
5. Post write/edit: editor segments, poll editor, draft status, and mobile submit actions.
6. Search: type chips, advanced filters, saved keywords, and result cards.
7. Login and account recovery: segmented account-help control and submit actions.
8. Signup: field labels, validation messages, and OAuth actions.
9. Onboarding: recommendation cards, push permission copy, and completion actions.
10. My page dashboard: profile actions, post sort, pagination, and comment list.
11. Settings: General, Notifications, and Security tabs plus all toggle descriptions.
12. Point history: transaction badges, descriptions, and pagination.
13. Saved posts: folder chips, search, and item actions.
14. Drafts and scheduled posts: status labels and row actions.
15. Messages: mailbox segments, participant names, and composer actions.
16. Notifications: type badges, message text, and unread states.
17. Reports and blocked users: status badges and row actions.
18. Recent posts and subscriptions: item metadata, drag handles, and count badges.
19. Public profile: profile tabs, badge cards, and representative-badge action.
20. Admin dashboard: range segments, SVG chart legend, filters, status labels, and audit table.

## Completion

- Run `npm run check:ui` before the manual viewport pass. It guards responsive route views and color-token usage.
- Run `npm run test:run` to cover heading hierarchy, tab relationships, shared page headers, and the fluid emoticon grid.
- Record each defect with route, viewport, locale, theme, and screenshot.
- Fix shared primitives before applying page-local overrides.
- Re-run the affected screen at all four widths and run `npm run check:colors`, `npm run type-check`, and targeted tests.

# Generated API Contract AGENTS.md

## Scope And Sources

[openapi-frontend.json](openapi-frontend.json) is generated from backend controllers/DTOs by [OpenApiSpecSnapshotTest](../../backend/src/test/java/com/weedrice/whiteboard/global/config/OpenApiSpecSnapshotTest.java); it then generates [frontend API types](../../frontend/src/types/generated/api.ts).

- Never hand-edit either generated output to bypass a mismatch.
- When represented contracts change, coordinate controller/DTO changes, [API명세서.md](../../backend/API명세서.md), [기능명세서.md](../../backend/기능명세서.md), [API revision](../ops/api-contract-revision.txt), generated types, and affected consumers.
- Generation is intentional contract maintenance; ordinary verification must not enable `UPDATE_OPENAPI_SNAPSHOT`. A passing generator proves synchronization, not backward compatibility.
- Use the JDK/Node setup in the module READMEs and follow both [backend](../../backend/AGENTS.md) and [frontend](../../frontend/AGENTS.md) instructions for affected behavior.

## Ordinary Verification

Run from the repository root in Windows PowerShell. The explicit project paths keep all commands rooted correctly without changing the shell directory. Check the backend contract and frontend output independently:

```powershell
Remove-Item Env:UPDATE_OPENAPI_SNAPSHOT -ErrorAction SilentlyContinue
& .\backend\gradlew.bat -p backend test --tests "*OpenApiSpecSnapshotTest" --rerun-tasks
if ($LASTEXITCODE -ne 0) { throw 'Backend API snapshot verification failed' }
& npm.cmd --prefix frontend run api:check
if ($LASTEXITCODE -ne 0) { throw 'Frontend API type verification failed' }
& npm.cmd --prefix frontend run type-check
if ($LASTEXITCODE -ne 0) { throw 'Frontend type-check failed' }
```

## Intentional Regeneration

1. Generate the snapshot, always clearing update mode even on failure:

```powershell
try {
    $env:UPDATE_OPENAPI_SNAPSHOT = 'true'
    & .\backend\gradlew.bat -p backend test --tests "*OpenApiSpecSnapshotTest" --rerun-tasks
    if ($LASTEXITCODE -ne 0) { throw 'OpenAPI snapshot generation failed' }
}
finally {
    Remove-Item Env:UPDATE_OPENAPI_SNAPSHOT -ErrorAction SilentlyContinue
}
```

2. Review the snapshot's semantic diff before generating frontend types:

```powershell
& npm.cmd --prefix frontend run api:generate
if ($LASTEXITCODE -ne 0) { throw 'Frontend API type generation failed' }
```

3. Review the generated type diff, then run ordinary verification above and the affected consumer tests. Do not leave update mode enabled or regenerate merely to hide an unexplained mismatch.

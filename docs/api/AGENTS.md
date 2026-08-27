# Generated API Contract AGENTS.md

## Scope

`openapi-frontend.json` is the generated backend-to-frontend OpenAPI snapshot. It is not an independently authored API specification.

- Do not hand-edit the snapshot or `frontend/src/types/generated/api.ts` to bypass a mismatch.
- Generate the snapshot from `OpenApiSpecSnapshotTest`, review its semantic diff, then regenerate and verify the frontend type output.
- Keep controller mappings, DTOs, `backend/API명세서.md`, `backend/기능명세서.md`, `docs/ops/api-contract-revision.txt`, generated types, and affected consumers synchronized when their contract changes.
- Do not enable snapshot update mode for ordinary verification or leave it set in the shell after generation.

## Windows Generation And Verification

From the repository root, run:

```powershell
$repositoryRoot = (Get-Location).Path
try {
    Set-Location backend
    $env:UPDATE_OPENAPI_SNAPSHOT = 'true'
    & .\gradlew.bat test --tests "*OpenApiSpecSnapshotTest" --rerun-tasks
    if ($LASTEXITCODE -ne 0) { throw 'OpenAPI snapshot generation failed' }

    Remove-Item Env:UPDATE_OPENAPI_SNAPSHOT
    Set-Location ..\frontend
    & npm.cmd run api:generate
    if ($LASTEXITCODE -ne 0) { throw 'Frontend API type generation failed' }
    & npm.cmd run api:check
    if ($LASTEXITCODE -ne 0) { throw 'Frontend API type verification failed' }
    & npm.cmd run type-check
    if ($LASTEXITCODE -ne 0) { throw 'Frontend type-check failed' }
}
finally {
    Remove-Item Env:UPDATE_OPENAPI_SNAPSHOT -ErrorAction SilentlyContinue
    Set-Location $repositoryRoot
}
```

Review the generated diffs before accepting them. A passing generator proves synchronization, not that an API change is backward-compatible or authorized.

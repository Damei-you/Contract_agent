param(
    [string] $BaseUrl = "http://localhost:8088"
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path

function Post-Json {
    param(
        [string] $Path,
        [string] $Body
    )
    $uri = "$BaseUrl$Path"
    Write-Host "POST $uri"
    Invoke-RestMethod `
        -Method Post `
        -Uri $uri `
        -ContentType "application/json; charset=utf-8" `
        -Body $Body | Out-Null
}

$policyBody = Get-Content -Raw -Encoding UTF8 (Join-Path $Root "seed-policies.json")
Post-Json -Path "/api/policies/import" -Body $policyBody

$contractSeed = Get-Content -Raw -Encoding UTF8 (Join-Path $Root "seed-contracts.json") | ConvertFrom-Json
foreach ($contract in $contractSeed.contracts) {
    $body = $contract | ConvertTo-Json -Depth 30
    Post-Json -Path "/api/contracts/import" -Body $body
}

$approvalSeed = Get-Content -Raw -Encoding UTF8 (Join-Path $Root "seed-approval-records.json") | ConvertFrom-Json
foreach ($group in $approvalSeed.contracts) {
    $body = @{ records = $group.records } | ConvertTo-Json -Depth 40
    Post-Json -Path "/api/contracts/$($group.contractId)/approval-records/import" -Body $body
}

Write-Host "Evaluation seed imported."

param(
    [string]$GatewayBaseUrl = "http://localhost:18081",
    [string]$DataServiceBaseUrl = "http://localhost:18082",
    [string]$UserName = "Holmes",
    [string]$BearerToken = "",
    [string]$AuthPassword = "",
    [string]$OutputFile = "",
    [Nullable[long]]$CleanTaskId = $null,
    [Nullable[long]]$FusionTaskId = $null,
    [int]$RepairLimit = 200,
    [switch]$AutoSelectTaskIds,
    [switch]$BootstrapIfMissing,
    [switch]$ReadOnly
)

$ErrorActionPreference = "Stop"

function New-Result {
    param(
        [string]$Name,
        [string]$Status,
        [string]$Detail
    )
    [PSCustomObject]@{
        Name = $Name
        Status = $Status
        Detail = $Detail
    }
}

function Invoke-Json {
    param(
        [ValidateSet("GET", "POST")]
        [string]$Method,
        [string]$Url,
        [hashtable]$Headers,
        $Body = $null,
        [int]$TimeoutSec = 15
    )

    if ($Method -eq "GET") {
        return Invoke-RestMethod -Method GET -Uri $Url -Headers $Headers -TimeoutSec $TimeoutSec
    }

    if ($null -eq $Body) {
        return Invoke-RestMethod -Method POST -Uri $Url -Headers $Headers -TimeoutSec $TimeoutSec
    }

    return Invoke-RestMethod -Method POST -Uri $Url -Headers $Headers -Body ($Body | ConvertTo-Json -Depth 20) -ContentType "application/json" -TimeoutSec $TimeoutSec
}

function Add-Check {
    param(
        [string]$Name,
        [scriptblock]$Script,
        [switch]$AllowUnauthorizedSkip
    )

    try {
        $detail = & $Script
        $script:Results.Add((New-Result -Name $Name -Status "PASS" -Detail ([string]$detail)))
    }
    catch {
        $msg = $_.Exception.Message
        $looksUnauthorized = $msg -like "*401*" -or $msg -like "*Unauthorized*" -or $msg -like "*未经授权*"
        if ($AllowUnauthorizedSkip.IsPresent -and $looksUnauthorized -and [string]::IsNullOrWhiteSpace($script:BearerToken)) {
            $script:Results.Add((New-Result -Name $Name -Status "SKIP" -Detail "gateway auth required; rerun with -BearerToken"))
            return
        }
        $script:Results.Add((New-Result -Name $Name -Status "FAIL" -Detail $msg))
    }
}

function Resolve-AuthToken {
    param(
        [string]$Gateway,
        [string]$User,
        [string]$Password,
        [string]$ExistingToken
    )

    if (-not [string]::IsNullOrWhiteSpace($ExistingToken)) {
        return $ExistingToken
    }

    if ([string]::IsNullOrWhiteSpace($Password)) {
        return ""
    }

    $loginBody = @{
        username = $User
        password = $Password
    }

    try {
        $loginResp = Invoke-RestMethod -Method POST -Uri "$Gateway/api/auth/login" -Body ($loginBody | ConvertTo-Json -Depth 5) -ContentType "application/json" -TimeoutSec 15
        $token = $null

        if ($null -ne $loginResp) {
            if ($loginResp.PSObject.Properties.Name -contains "token") {
                $token = [string]$loginResp.token
            }
            elseif ($loginResp.PSObject.Properties.Name -contains "data") {
                $data = $loginResp.data
                if ($null -ne $data -and $data.PSObject.Properties.Name -contains "token") {
                    $token = [string]$data.token
                }
            }
        }

        if ([string]::IsNullOrWhiteSpace($token)) {
            throw "login succeeded but token field is missing"
        }

        return $token
    }
    catch {
        throw "failed to acquire token from /api/auth/login: $($_.Exception.Message)"
    }
}

function Try-GetDataArray {
    param(
        $Response
    )

    if ($null -eq $Response) {
        return @()
    }

    if ($Response -is [System.Array]) {
        return @($Response)
    }

    if ($Response -is [System.Collections.IEnumerable] -and -not ($Response -is [string])) {
        $asList = @($Response)
        if ($asList.Count -gt 0 -and -not ($asList[0] -is [System.Collections.DictionaryEntry])) {
            return $asList
        }
    }

    if ($Response.PSObject -and ($Response.PSObject.Properties.Name -contains "data")) {
        $data = $Response.data
        if ($null -eq $data) {
            return @()
        }
        return @($data)
    }

    return @()
}

function Resolve-LatestTaskId {
    param(
        [string]$Gateway,
        [hashtable]$Headers,
        [string]$TaskType,
        [switch]$AllowUnauthorizedSkip
    )

    $taskTypeUpper = ""
    if (-not [string]::IsNullOrWhiteSpace($TaskType)) {
        $taskTypeUpper = $TaskType.Trim().ToUpperInvariant()
    }
    if ($taskTypeUpper -ne "CLEAN" -and $taskTypeUpper -ne "FUSION") {
        throw "unsupported taskType: $TaskType"
    }

    $path = if ($taskTypeUpper -eq "CLEAN") { "clean" } else { "fusion" }
    $url = "$Gateway/api/data/$path/tasks?status=COMPLETED"

    try {
        $resp = Invoke-Json -Method GET -Url $url -Headers $Headers
        $items = Try-GetDataArray -Response $resp
        if ($items.Count -lt 1) {
            return $null
        }

        $idVal = $items[0].id
        if ($null -eq $idVal) {
            return $null
        }

        return [long]$idVal
    }
    catch {
        $msg = $_.Exception.Message
        $looksUnauthorized = $msg -like "*401*" -or $msg -like "*Unauthorized*" -or $msg -like "*未经授权*"
        if ($AllowUnauthorizedSkip.IsPresent -and $looksUnauthorized -and [string]::IsNullOrWhiteSpace($script:BearerToken)) {
            return $null
        }
        throw
    }
}

$gateway = $GatewayBaseUrl.TrimEnd("/")
$dataService = $DataServiceBaseUrl.TrimEnd("/")
$BearerToken = Resolve-AuthToken -Gateway $gateway -User $UserName -Password $AuthPassword -ExistingToken $BearerToken
$headers = @{ "X-User-Name" = $UserName }
if (-not [string]::IsNullOrWhiteSpace($BearerToken)) {
    $headers["Authorization"] = "Bearer $BearerToken"
}
$Results = New-Object System.Collections.Generic.List[object]

if ($AutoSelectTaskIds.IsPresent) {
    if ($null -eq $CleanTaskId) {
        $CleanTaskId = Resolve-LatestTaskId -Gateway $gateway -Headers $headers -TaskType "CLEAN" -AllowUnauthorizedSkip
    }
    if ($null -eq $FusionTaskId) {
        $FusionTaskId = Resolve-LatestTaskId -Gateway $gateway -Headers $headers -TaskType "FUSION" -AllowUnauthorizedSkip
    }
}

Add-Check -Name "01 Gateway health" -Script {
    $r = Invoke-Json -Method GET -Url "$gateway/actuator/health" -Headers @{}
    if ($r.status -ne "UP") { throw "gateway health status=$($r.status)" }
    "gateway UP"
}

Add-Check -Name "02 Data-service health" -Script {
    $r = Invoke-Json -Method GET -Url "$dataService/actuator/health" -Headers @{}
    if ($r.status -ne "UP") { throw "data-service health status=$($r.status)" }
    "data-service UP"
}

Add-Check -Name "03 NiFi status via control-plane" -Script {
    $r = Invoke-Json -Method GET -Url "$gateway/api/data/control-plane/nifi/status" -Headers $headers
    $data = $r.data
    if (-not $data.enabled) { throw "nifi enabled=false" }
    if (-not $data.reachable) { throw "nifi reachable=false" }
    "enabled=$($data.enabled), reachable=$($data.reachable)"
} -AllowUnauthorizedSkip

Add-Check -Name "04 NiFi templates ready" -Script {
    $r = Invoke-Json -Method GET -Url "$gateway/api/data/control-plane/nifi/templates" -Headers $headers
    $items = @($r.data)
    $clean = $items | Where-Object { $_.flowType -eq "CLEAN" -and $_.enabled -eq $true }
    $fusion = $items | Where-Object { $_.flowType -eq "FUSION" -and $_.enabled -eq $true }

    if ($clean.Count -lt 1 -or $fusion.Count -lt 1) {
        if ($BootstrapIfMissing.IsPresent -and -not $ReadOnly.IsPresent) {
            Invoke-Json -Method POST -Url "$gateway/api/data/control-plane/nifi/templates/bootstrap" -Headers $headers | Out-Null
            $r2 = Invoke-Json -Method GET -Url "$gateway/api/data/control-plane/nifi/templates" -Headers $headers
            $items2 = @($r2.data)
            $clean2 = $items2 | Where-Object { $_.flowType -eq "CLEAN" -and $_.enabled -eq $true }
            $fusion2 = $items2 | Where-Object { $_.flowType -eq "FUSION" -and $_.enabled -eq $true }
            if ($clean2.Count -lt 1 -or $fusion2.Count -lt 1) {
                throw "templates still missing after bootstrap"
            }
            return "bootstrap invoked and templates ready"
        }
        throw "CLEAN/FUSION enabled templates missing"
    }

    "CLEAN and FUSION templates are enabled"
} -AllowUnauthorizedSkip

if ($CleanTaskId -ne $null) {
    Add-Check -Name "05 Reconcile one CLEAN task" -Script {
        if ($ReadOnly.IsPresent) { return "skipped in read-only mode" }
        $body = @{ taskType = "CLEAN"; taskId = $CleanTaskId }
        $r = Invoke-Json -Method POST -Url "$gateway/api/data/control-plane/nifi/tasks/reconcile/one" -Headers $headers -Body $body
        "outcome=$($r.data.outcome)"
    }

    Add-Check -Name "06 CLEAN layer stats" -Script {
        $r = Invoke-Json -Method GET -Url "$gateway/api/data/control-plane/layers/stats?taskType=CLEAN&taskId=$CleanTaskId" -Headers $headers
        $summary = $r.data.summary
        "bronze=$($summary.bronzeRows), silver=$($summary.silverRows)"
    }
}
else {
    $Results.Add((New-Result -Name "05 Reconcile one CLEAN task" -Status "SKIP" -Detail "CleanTaskId not provided"))
    $Results.Add((New-Result -Name "06 CLEAN layer stats" -Status "SKIP" -Detail "CleanTaskId not provided"))
}

if ($FusionTaskId -ne $null) {
    Add-Check -Name "07 Reconcile one FUSION task" -Script {
        if ($ReadOnly.IsPresent) { return "skipped in read-only mode" }
        $body = @{ taskType = "FUSION"; taskId = $FusionTaskId }
        $r = Invoke-Json -Method POST -Url "$gateway/api/data/control-plane/nifi/tasks/reconcile/one" -Headers $headers -Body $body
        "outcome=$($r.data.outcome)"
    }

    Add-Check -Name "08 FUSION layer stats" -Script {
        $r = Invoke-Json -Method GET -Url "$gateway/api/data/control-plane/layers/stats?taskType=FUSION&taskId=$FusionTaskId" -Headers $headers
        $summary = $r.data.summary
        "gold=$($summary.goldRows)"
    }
}
else {
    $Results.Add((New-Result -Name "07 Reconcile one FUSION task" -Status "SKIP" -Detail "FusionTaskId not provided"))
    $Results.Add((New-Result -Name "08 FUSION layer stats" -Status "SKIP" -Detail "FusionTaskId not provided"))
}

Add-Check -Name "09 Batch repair artifacts" -Script {
    if ($ReadOnly.IsPresent) { return "skipped in read-only mode" }
    $r = Invoke-Json -Method POST -Url "$gateway/api/data/control-plane/nifi/tasks/repair-artifacts?limit=$RepairLimit" -Headers $headers
    $d = $r.data
    "repairedTotal=$($d.repairedTotal), scannedClean=$($d.scannedClean), scannedFusion=$($d.scannedFusion)"
}

Add-Check -Name "10 Batch reconcile and history" -Script {
    if ($ReadOnly.IsPresent) {
        $hist = Invoke-Json -Method GET -Url "$gateway/api/data/control-plane/nifi/tasks/reconcile/history?limit=5" -Headers $headers
        return "history read-only count=$(@($hist.data).Count)"
    }

    Invoke-Json -Method POST -Url "$gateway/api/data/control-plane/nifi/tasks/reconcile?limit=100" -Headers $headers | Out-Null
    $r2 = Invoke-Json -Method GET -Url "$gateway/api/data/control-plane/nifi/tasks/reconcile/history?limit=5" -Headers $headers
    "reconcile done, history count=$(@($r2.data).Count)"
} -AllowUnauthorizedSkip

Write-Host ""
Write-Host "NIFI-ONLY GO-LIVE CHECK REPORT" -ForegroundColor Cyan
Write-Host "Gateway: $gateway"
Write-Host "DataService: $dataService"
Write-Host "User: $UserName"
Write-Host "AuthToken: $([string]::IsNullOrWhiteSpace($BearerToken) -eq $false)"
Write-Host "AutoSelectTaskIds: $($AutoSelectTaskIds.IsPresent)"
$cleanTaskDisplay = "<auto-miss>"
if ($null -ne $CleanTaskId) {
    $cleanTaskDisplay = [string]$CleanTaskId
}
$fusionTaskDisplay = "<auto-miss>"
if ($null -ne $FusionTaskId) {
    $fusionTaskDisplay = [string]$FusionTaskId
}
Write-Host "CleanTaskId: $cleanTaskDisplay"
Write-Host "FusionTaskId: $fusionTaskDisplay"
Write-Host "ReadOnly: $($ReadOnly.IsPresent)"
Write-Host ""

$pass = 0
$fail = 0
$skip = 0

foreach ($item in $Results) {
    $color = "White"
    if ($item.Status -eq "PASS") { $color = "Green"; $pass++ }
    elseif ($item.Status -eq "FAIL") { $color = "Red"; $fail++ }
    else { $color = "Yellow"; $skip++ }

    Write-Host ("[{0}] {1} :: {2}" -f $item.Status, $item.Name, $item.Detail) -ForegroundColor $color
}

Write-Host ""
Write-Host ("Summary: PASS={0}, FAIL={1}, SKIP={2}" -f $pass, $fail, $skip) -ForegroundColor Cyan

$report = [ordered]@{
    generatedAt = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")
    gateway = $gateway
    dataService = $dataService
    user = $UserName
    hasAuthToken = (-not [string]::IsNullOrWhiteSpace($BearerToken))
    readOnly = $ReadOnly.IsPresent
    autoSelectTaskIds = $AutoSelectTaskIds.IsPresent
    cleanTaskId = $CleanTaskId
    fusionTaskId = $FusionTaskId
    summary = [ordered]@{
        pass = $pass
        fail = $fail
        skip = $skip
    }
    checks = @(
        $Results | ForEach-Object {
            [ordered]@{
                name = $_.Name
                status = $_.Status
                detail = $_.Detail
            }
        }
    )
}

if (-not [string]::IsNullOrWhiteSpace($OutputFile)) {
    $resolvedOutput = $OutputFile
    if (-not [System.IO.Path]::IsPathRooted($resolvedOutput)) {
        $resolvedOutput = Join-Path (Get-Location) $resolvedOutput
    }

    $parentDir = Split-Path -Parent $resolvedOutput
    if (-not [string]::IsNullOrWhiteSpace($parentDir) -and -not (Test-Path $parentDir)) {
        New-Item -ItemType Directory -Path $parentDir -Force | Out-Null
    }

    ($report | ConvertTo-Json -Depth 20) | Out-File -FilePath $resolvedOutput -Encoding UTF8
    Write-Host ("Report saved: {0}" -f $resolvedOutput) -ForegroundColor Cyan
}

if ($fail -gt 0) {
    exit 2
}

exit 0

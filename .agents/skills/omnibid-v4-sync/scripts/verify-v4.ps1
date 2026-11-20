# verify-v4.ps1 - Omnibid backend v4 conformance check (ASCII only, Windows PowerShell 5.1+)
# Usage (repo root): powershell -ExecutionPolicy Bypass -File .agents\skills\omnibid-v4-sync\scripts\verify-v4.ps1
param([string]$Root = (Get-Location).Path)

$ErrorActionPreference = 'Stop'
$fail = 0
function Bad($msg) { Write-Host "[FAIL] $msg" -ForegroundColor Red; $script:fail++ }
function Good($msg) { Write-Host "[ OK ] $msg" -ForegroundColor Green }

$main = Join-Path $Root 'src\main'
$javaMain = Join-Path $Root 'src\main\java'
$javaFiles = Get-ChildItem -Path $javaMain -Recurse -Filter *.java

# 1. Forbidden terms in src/main (regex, case-sensitive)
$forbidden = @(
  'LocalDateTime', 'BidStatus', 'INVALIDATED', 'invalidReason', 'ReportAction', 'ReportType\b',
  'Shipment', 'Watchlist', 'AuditLog', 'auditService', 'iconUrl', 'icon_url',
  'precision\s*=\s*12', 'scale\s*=\s*2\b', '/ws-auction', '/topic/auction/', 'withSockJS',
  'PaymentStatus\.PENDING', 'SimulatedOutcome', 'MOCK_BANK', 'MOCK_WALLET', 'transactionRef',
  'UNPROCESSABLE_ENTITY', 'AppException', 'LOCK IN SHARE', 'FOR SHARE', 'ProductStatus\.REMOVED',
  'ProductCondition\.USED', 'AUCTION_WON', 'ORDER_CREATED', 'PAYMENT_RESULT',
  'PAYMENT_REMINDER', 'SHIPMENT_UPDATE', 'LocalStorageServiceImpl', 'reminder', 'deliveredAt',
  'allowedOriginPatterns\("\*"\)', 'ddl-auto\s*=\s*(update|create)'
)
foreach ($term in $forbidden) {
  $hits = Get-ChildItem -Path $main -Recurse -Include *.java,*.properties |
          Select-String -Pattern $term -CaseSensitive
  if ($hits) {
    Bad ("forbidden '" + $term + "' found " + $hits.Count + "x, e.g. " + $hits[0].Path.Replace($Root,'') + ":" + $hits[0].LineNumber)
  }
}
if ($fail -eq 0) { Good 'no forbidden legacy terms in src/main' }

# 2. No YAML config
$yml = Get-ChildItem -Path (Join-Path $Root 'src') -Recurse -Include *.yml,*.yaml -ErrorAction SilentlyContinue
if ($yml) { Bad ("YAML config present: " + (($yml | ForEach-Object { $_.Name }) -join ', ')) } else { Good 'no YAML config' }

# 3. Flyway migrations identical to the v4 reference scripts
$refDir = Join-Path $Root '.agents\skills\omnibid-v4-sync\references\sql'
$migDir = Join-Path $Root 'src\main\resources\db\migration'
foreach ($f in @('V1__init_schema.sql','V2__seed_data.sql')) {
  $mig = Join-Path $migDir $f
  if (-not (Test-Path $mig)) { Bad "missing migration $f"; continue }
  $a = (Get-Content (Join-Path $refDir $f) -Encoding UTF8 | Where-Object { $_ -notmatch '^\s*--' -and $_.Trim() -ne '' }) -join "`n"
  $b = (Get-Content $mig -Encoding UTF8 | Where-Object { $_ -notmatch '^\s*--' -and $_.Trim() -ne '' }) -join "`n"
  if ($a -ne $b) { Bad "$f differs from reference (record the reason in docs/v4/PROGRESS.md if intentional)" } else { Good "$f matches reference" }
}
$extra = Get-ChildItem $migDir -Filter *.sql -ErrorAction SilentlyContinue | Where-Object { $_.Name -notin @('V1__init_schema.sql','V2__seed_data.sql') }
if ($extra) { Write-Host ("[WARN] extra migrations: " + (($extra | ForEach-Object { $_.Name }) -join ', ')) -ForegroundColor Yellow }

# 4. Exactly 10 @Table entities
$tables = $javaFiles | Select-String -Pattern '@Table\(\s*name\s*=\s*"([a-z_]+)"' | ForEach-Object { $_.Matches[0].Groups[1].Value } | Sort-Object -Unique
$expectedTables = @('auctions','bids','categories','notifications','orders','payments','product_images','products','reports','users')
if (-not $tables) { $tables = @() }
$diffT = Compare-Object $expectedTables $tables
if ($diffT) { Bad ("entity tables mismatch: " + (($diffT | ForEach-Object { $_.SideIndicator + $_.InputObject }) -join ' ')) } else { Good '10 entity tables match' }

# 5. REST endpoints == the 54 endpoints of the v4 design
$expected = @(
 'POST /api/v1/auth/register','POST /api/v1/auth/login','POST /api/v1/auth/logout',
 'GET /api/v1/users/me','PATCH /api/v1/users/me','PATCH /api/v1/users/me/password','POST /api/v1/users/me/avatar',
 'GET /api/v1/categories','POST /api/v1/products','GET /api/v1/products/mine','GET /api/v1/products/{}',
 'PATCH /api/v1/products/{}','DELETE /api/v1/products/{}','POST /api/v1/products/{}/images','DELETE /api/v1/products/{}/images/{}',
 'GET /api/v1/auctions','GET /api/v1/auctions/{}','POST /api/v1/auctions','PATCH /api/v1/auctions/{}','POST /api/v1/auctions/{}/cancel',
 'GET /api/v1/auctions/mine','GET /api/v1/auctions/{}/bids','POST /api/v1/auctions/{}/bids','GET /api/v1/bids/mine',
 'GET /api/v1/orders','GET /api/v1/orders/{}','PATCH /api/v1/orders/{}/shipping-info','POST /api/v1/orders/{}/payments',
 'GET /api/v1/orders/{}/payments','POST /api/v1/orders/{}/ship','POST /api/v1/orders/{}/confirm-receipt',
 'GET /api/v1/notifications','POST /api/v1/notifications/{}/read','POST /api/v1/reports',
 'GET /api/v1/admin/users','POST /api/v1/admin/users/{}/ban','POST /api/v1/admin/users/{}/unban',
 'GET /api/v1/admin/products','GET /api/v1/admin/products/{}','POST /api/v1/admin/products/{}/hide',
 'GET /api/v1/admin/auctions','GET /api/v1/admin/auctions/{}','POST /api/v1/admin/auctions/{}/cancel',
 'GET /api/v1/admin/orders','GET /api/v1/admin/orders/{}','POST /api/v1/admin/orders/{}/cancel',
 'GET /api/v1/admin/reports','GET /api/v1/admin/reports/{}','POST /api/v1/admin/reports/{}/resolve',
 'GET /api/v1/admin/categories','POST /api/v1/admin/categories','PUT /api/v1/admin/categories/{}','DELETE /api/v1/admin/categories/{}',
 'GET /api/v1/admin/statistics'
)
$found = New-Object System.Collections.Generic.List[string]
$ctrlFiles = $javaFiles | Where-Object { $_.FullName -match '\\controller\\' }
foreach ($cf in $ctrlFiles) {
  $text = Get-Content $cf.FullName -Raw -Encoding UTF8
  $base = ''
  $ci = $text.IndexOf(' class ')
  $head = $text
  if ($ci -gt 0) { $head = $text.Substring(0, $ci) }
  $m = [regex]::Match($head, '@RequestMapping\(\s*(?:value\s*=\s*|path\s*=\s*)?"([^"]*)"')
  if ($m.Success) { $base = $m.Groups[1].Value }
  $body = $text
  if ($ci -gt 0) { $body = $text.Substring($ci) }
  $ms = [regex]::Matches($body, '@(Get|Post|Put|Patch|Delete)Mapping(?:\(\s*(?:value\s*=\s*|path\s*=\s*)?(?:"([^"]*)")?[^)]*\))?')
  foreach ($x in $ms) {
    $p = $base + $x.Groups[2].Value
    $p = [regex]::Replace($p, '\{[^}]+\}', '{}').TrimEnd('/')
    $found.Add(($x.Groups[1].Value.ToUpper() + ' ' + $p))
  }
}
$foundU = $found | Sort-Object -Unique
$missing = $expected | Where-Object { $foundU -notcontains $_ }
$extraE  = $foundU | Where-Object { $expected -notcontains $_ }
if ($missing) { Bad ("missing endpoints (" + $missing.Count + "): " + ($missing -join '; ')) }
if ($extraE)  { Bad ("endpoints not in v4 (" + $extraE.Count + "): " + ($extraE -join '; ')) }
if (-not $missing -and -not $extraE) { Good ("REST endpoints = " + $expected.Count + " (v4)") }
if ($found.Count -ne $foundU.Count) { Write-Host "[WARN] duplicated mappings detected" -ForegroundColor Yellow }

# 6. ErrorCode enum values (Table 4.22)
$codes = @('VALIDATION_ERROR','INVALID_IMAGE','UNAUTHORIZED','FORBIDDEN','ACCOUNT_BANNED','NOT_FOUND','AUCTION_NOT_ACTIVE',
 'BID_TOO_LOW','ALREADY_LEADING','SELF_BID_NOT_ALLOWED','AUCTION_NOT_EDITABLE','CANNOT_CANCEL','PRODUCT_NOT_AVAILABLE',
 'PRODUCT_NOT_EDITABLE','INVALID_STATUS_TRANSITION','CANNOT_BAN_ADMIN','CATEGORY_IN_USE','ORDER_EXPIRED','ORDER_CANCELLED',
 'CONFLICT','PAYLOAD_TOO_LARGE','TOO_MANY_REQUESTS','INTERNAL_ERROR','IMAGE_UPLOAD_FAILED')
$ec = $javaFiles | Where-Object { $_.Name -eq 'ErrorCode.java' } | Select-Object -First 1
if (-not $ec) { Bad 'ErrorCode.java not found' } else {
  $ect = Get-Content $ec.FullName -Raw
  $miss = $codes | Where-Object { $ect -notmatch ('\b' + $_ + '\b') }
  if ($miss) { Bad ("ErrorCode missing: " + ($miss -join ', ')) } else { Good 'ErrorCode has all 24 codes' }
}

# 7. Demo profile
$demo = Join-Path $Root 'src\main\resources\application-demo.properties'
if (Test-Path $demo) {
  $d = Get-Content $demo -Raw
  foreach ($k in @('app.auction.min-duration-minutes=2','app.order.payment-deadline-hours=3m','app.order.confirm-deadline-days=3m','app.scheduler.interval-seconds=5','app.payment.simulate-enabled=true')) {
    if ($d -notmatch [regex]::Escape($k)) { Bad "application-demo.properties missing '$k'" }
  }
} else { Bad 'application-demo.properties missing' }

Write-Host ''
if ($fail -eq 0) { Write-Host 'ALL v4 CHECKS PASSED' -ForegroundColor Green; exit 0 }
else { Write-Host "$fail check(s) failed" -ForegroundColor Red; exit 1 }

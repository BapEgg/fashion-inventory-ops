[CmdletBinding()]
param(
    [string]$SeedDirectory = (Join-Path $PSScriptRoot '..\data\seed')
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$stockpilotSeedDirectory = [System.IO.Path]::GetFullPath($SeedDirectory)
$stockpilotMvp2Directory = Join-Path $stockpilotSeedDirectory 'mvp2'
$stockpilotInvariantCulture = [System.Globalization.CultureInfo]::InvariantCulture

function Import-StockPilotCsv {
    param(
        [string]$RelativePath,
        [string[]]$ExpectedHeaders
    )

    $stockpilotPath = Join-Path $stockpilotSeedDirectory $RelativePath
    if (-not (Test-Path -LiteralPath $stockpilotPath)) {
        throw "Missing Seed file: $RelativePath"
    }

    $stockpilotHeader = (Get-Content -LiteralPath $stockpilotPath -TotalCount 1).Split(',')
    if (($ExpectedHeaders -join ',') -cne ($stockpilotHeader -join ',')) {
        throw "Unexpected headers in $($RelativePath). Expected: $($ExpectedHeaders -join ',')"
    }

    $stockpilotRows = @(Import-Csv -LiteralPath $stockpilotPath)
    if ($stockpilotRows.Count -eq 0) {
        throw "Seed file has no rows: $RelativePath"
    }
    return $stockpilotRows
}

function Assert-UniqueKey {
    param(
        [object[]]$Rows,
        [scriptblock]$Key,
        [string]$Label
    )

    $stockpilotDuplicates = @($Rows | Group-Object -Property { & $Key $_ } | Where-Object Count -gt 1)
    if ($stockpilotDuplicates.Count -gt 0) {
        throw "Duplicate $($Label): $($stockpilotDuplicates.Name -join ', ')"
    }
}

function ConvertTo-StockPilotInteger {
    param(
        [string]$Value,
        [string]$Label,
        [switch]$Positive
    )

    if ($Value -notmatch '^\d+$') {
        throw "$Label must be an integer: $Value"
    }

    $stockpilotNumber = [long]$Value
    if ($Positive -and $stockpilotNumber -le 0) {
        throw "$Label must be positive: $Value"
    }
    return $stockpilotNumber
}

function ConvertTo-StockPilotDecimal {
    param(
        [string]$Value,
        [string]$Label,
        [switch]$Positive
    )

    $stockpilotNumber = 0D
    if (-not [decimal]::TryParse(
        $Value,
        [System.Globalization.NumberStyles]::Number,
        $stockpilotInvariantCulture,
        [ref]$stockpilotNumber
    )) {
        throw "$Label must be a decimal: $Value"
    }
    if ($stockpilotNumber -lt 0 -or ($Positive -and $stockpilotNumber -le 0)) {
        throw "$Label has an invalid sign: $Value"
    }
    return $stockpilotNumber
}

function ConvertTo-StockPilotDate {
    param(
        [string]$Value,
        [string]$Label
    )

    $stockpilotDate = [datetime]::MinValue
    if (-not [datetime]::TryParseExact(
        $Value,
        'yyyy-MM-dd',
        $stockpilotInvariantCulture,
        [System.Globalization.DateTimeStyles]::None,
        [ref]$stockpilotDate
    )) {
        throw "$Label must use yyyy-MM-dd: $Value"
    }
    return $stockpilotDate
}

function ConvertTo-StockPilotTimestamp {
    param(
        [string]$Value,
        [string]$Label
    )

    $stockpilotTimestamp = [datetimeoffset]::MinValue
    if (-not [datetimeoffset]::TryParseExact(
        $Value,
        "yyyy-MM-dd'T'HH:mm:sszzz",
        $stockpilotInvariantCulture,
        [System.Globalization.DateTimeStyles]::None,
        [ref]$stockpilotTimestamp
    )) {
        throw "$Label must include a timezone offset: $Value"
    }
    return $stockpilotTimestamp
}

function New-StockPilotKeyMap {
    param(
        [object[]]$Rows,
        [string]$Property
    )

    $stockpilotMap = @{}
    foreach ($stockpilotRow in $Rows) {
        $stockpilotMap[$stockpilotRow.$Property] = $stockpilotRow
    }
    return $stockpilotMap
}

# MVP-1 Golden Scenario remains immutable and is validated first.
$stockpilotProducts = @(Import-StockPilotCsv 'products.csv' @('sku_id', 'product_name', 'category', 'color', 'size'))
$stockpilotStores = @(Import-StockPilotCsv 'stores.csv' @('store_id', 'store_name', 'region'))
$stockpilotInventory = @(Import-StockPilotCsv 'inventory.csv' @('snapshot_date', 'store_id', 'sku_id', 'on_hand_quantity', 'reserved_quantity'))
$stockpilotSales = @(Import-StockPilotCsv 'sales.csv' @('sales_date', 'store_id', 'sku_id', 'sold_quantity'))

Assert-UniqueKey $stockpilotProducts { param($row) $row.sku_id } 'MVP-1 sku_id'
Assert-UniqueKey $stockpilotStores { param($row) $row.store_id } 'MVP-1 store_id'
Assert-UniqueKey $stockpilotInventory { param($row) "$($row.snapshot_date)|$($row.store_id)|$($row.sku_id)" } 'MVP-1 inventory key'
Assert-UniqueKey $stockpilotSales { param($row) "$($row.sales_date)|$($row.store_id)|$($row.sku_id)" } 'MVP-1 sales key'

$stockpilotProductKeys = New-StockPilotKeyMap $stockpilotProducts 'sku_id'
$stockpilotStoreKeys = New-StockPilotKeyMap $stockpilotStores 'store_id'

foreach ($stockpilotRow in $stockpilotInventory) {
    if (-not $stockpilotProductKeys.ContainsKey($stockpilotRow.sku_id)) { throw "Unknown MVP-1 inventory sku_id: $($stockpilotRow.sku_id)" }
    if (-not $stockpilotStoreKeys.ContainsKey($stockpilotRow.store_id)) { throw "Unknown MVP-1 inventory store_id: $($stockpilotRow.store_id)" }
    $stockpilotOnHand = ConvertTo-StockPilotInteger $stockpilotRow.on_hand_quantity 'MVP-1 on_hand_quantity'
    $stockpilotReserved = ConvertTo-StockPilotInteger $stockpilotRow.reserved_quantity 'MVP-1 reserved_quantity'
    if ($stockpilotReserved -gt $stockpilotOnHand) { throw "Reserved quantity exceeds on-hand quantity: $($stockpilotRow.store_id)" }
}

foreach ($stockpilotRow in $stockpilotSales) {
    if (-not $stockpilotProductKeys.ContainsKey($stockpilotRow.sku_id)) { throw "Unknown MVP-1 sales sku_id: $($stockpilotRow.sku_id)" }
    if (-not $stockpilotStoreKeys.ContainsKey($stockpilotRow.store_id)) { throw "Unknown MVP-1 sales store_id: $($stockpilotRow.store_id)" }
    $null = ConvertTo-StockPilotInteger $stockpilotRow.sold_quantity 'MVP-1 sold_quantity'
}

if ($stockpilotProducts.Count -ne 1 -or $stockpilotStores.Count -ne 3 -or $stockpilotInventory.Count -ne 3 -or $stockpilotSales.Count -ne 21) {
    throw 'Golden Scenario row counts must be 1 product, 3 stores, 3 inventory rows and 21 sales rows.'
}

$stockpilotGangnamInventory = $stockpilotInventory | Where-Object store_id -eq 'STORE-GANGNAM'
$stockpilotGangnamAvailable = [int]$stockpilotGangnamInventory.on_hand_quantity - [int]$stockpilotGangnamInventory.reserved_quantity
$stockpilotGangnamSales = ($stockpilotSales | Where-Object store_id -eq 'STORE-GANGNAM' | Measure-Object -Property sold_quantity -Sum).Sum
$stockpilotHongdaeInventory = $stockpilotInventory | Where-Object store_id -eq 'STORE-HONGDAE'
$stockpilotHongdaeAvailable = [int]$stockpilotHongdaeInventory.on_hand_quantity - [int]$stockpilotHongdaeInventory.reserved_quantity
$stockpilotHongdaeSales = ($stockpilotSales | Where-Object store_id -eq 'STORE-HONGDAE' | Measure-Object -Property sold_quantity -Sum).Sum

if ($stockpilotGangnamAvailable -ne 5 -or $stockpilotGangnamSales -ne 28) { throw 'Gangnam Golden Scenario values do not match the specification.' }
if ($stockpilotHongdaeAvailable -ne 40 -or $stockpilotHongdaeSales -ne 4) { throw 'Hongdae Golden Scenario values do not match the specification.' }

# MVP-2 input contract. Values are versioned demo ASSUMPTION/SYNTHETIC data.
if (-not (Test-Path -LiteralPath $stockpilotMvp2Directory -PathType Container)) {
    throw 'Missing MVP-2 Seed directory: mvp2'
}

$stockpilotMvp2Products = @(Import-StockPilotCsv 'mvp2\products.csv' @(
    'sku_id', 'product_name', 'category', 'color', 'size',
    'launch_date', 'season_code', 'sales_status'
))
$stockpilotMvp2Stores = @(Import-StockPilotCsv 'mvp2\stores.csv' @(
    'store_id', 'store_name', 'region', 'store_type', 'inventory_owner_code', 'transfer_zone'
))
$stockpilotMvp2Inventory = @(Import-StockPilotCsv 'mvp2\inventory-daily.csv' @(
    'snapshot_date', 'snapshot_at', 'store_id', 'sku_id', 'on_hand_quantity',
    'reserved_quantity', 'out_of_stock_flag', 'input_snapshot_version', 'source_type'
))
$stockpilotMvp2Sales = @(Import-StockPilotCsv 'mvp2\sales-daily.csv' @(
    'sales_date', 'store_id', 'sku_id', 'sold_quantity', 'transaction_count',
    'max_transaction_quantity', 'average_selling_price', 'input_snapshot_version', 'source_type'
))
$stockpilotMvp2Events = @(Import-StockPilotCsv 'mvp2\demand-events.csv' @(
    'event_code', 'event_type', 'store_id', 'sku_id', 'start_date', 'end_date',
    'uplift_low', 'uplift_base', 'uplift_high', 'input_snapshot_version',
    'source_type', 'assumption_type'
))
$stockpilotMvp2Inbound = @(Import-StockPilotCsv 'mvp2\inbound-schedules.csv' @(
    'inbound_reference', 'store_id', 'sku_id', 'quantity', 'eta_at',
    'inbound_status', 'input_snapshot_version', 'source_type'
))
$stockpilotMvp2OpenTransfers = @(Import-StockPilotCsv 'mvp2\open-transfers.csv' @(
    'transfer_reference', 'donor_store_id', 'receiver_store_id', 'sku_id',
    'quantity', 'eta_at', 'transfer_status', 'input_snapshot_version', 'source_type'
))
$stockpilotMvp2Routes = @(Import-StockPilotCsv 'mvp2\transfer-routes.csv' @(
    'donor_store_id', 'receiver_store_id', 'active_flag', 'owner_override_flag',
    'lead_time_days', 'minimum_quantity', 'package_multiple', 'maximum_quantity',
    'input_snapshot_version', 'assumption_type'
))
$stockpilotMvp2Policies = @(Import-StockPilotCsv 'mvp2\store-sku-policies.csv' @(
    'store_id', 'sku_id', 'display_minimum', 'safety_stock', 'maximum_capacity',
    'target_coverage_days', 'retained_days', 'input_snapshot_version', 'assumption_type'
))

if (
    $stockpilotMvp2Products.Count -ne 6 -or
    $stockpilotMvp2Stores.Count -ne 3 -or
    $stockpilotMvp2Inventory.Count -ne 348 -or
    $stockpilotMvp2Sales.Count -ne 336 -or
    $stockpilotMvp2Events.Count -ne 1 -or
    $stockpilotMvp2Inbound.Count -ne 1 -or
    $stockpilotMvp2OpenTransfers.Count -ne 1 -or
    $stockpilotMvp2Routes.Count -ne 2 -or
    $stockpilotMvp2Policies.Count -ne 12
) {
    throw 'MVP-2 row counts must be 6 products, 3 stores, 348 inventory, 336 sales, 1 event, 1 inbound, 1 open transfer, 2 routes and 12 policies.'
}

Assert-UniqueKey $stockpilotMvp2Products { param($row) $row.sku_id } 'MVP-2 sku_id'
Assert-UniqueKey $stockpilotMvp2Stores { param($row) $row.store_id } 'MVP-2 store_id'
Assert-UniqueKey $stockpilotMvp2Inventory { param($row) "$($row.snapshot_date)|$($row.store_id)|$($row.sku_id)|$($row.input_snapshot_version)" } 'MVP-2 inventory key'
Assert-UniqueKey $stockpilotMvp2Sales { param($row) "$($row.sales_date)|$($row.store_id)|$($row.sku_id)|$($row.input_snapshot_version)" } 'MVP-2 sales key'
Assert-UniqueKey $stockpilotMvp2Events { param($row) "$($row.event_code)|$($row.store_id)|$($row.sku_id)|$($row.input_snapshot_version)" } 'MVP-2 event key'
Assert-UniqueKey $stockpilotMvp2Inbound { param($row) "$($row.inbound_reference)|$($row.input_snapshot_version)" } 'MVP-2 inbound key'
Assert-UniqueKey $stockpilotMvp2OpenTransfers { param($row) "$($row.transfer_reference)|$($row.input_snapshot_version)" } 'MVP-2 open transfer key'
Assert-UniqueKey $stockpilotMvp2Routes { param($row) "$($row.donor_store_id)|$($row.receiver_store_id)|$($row.input_snapshot_version)" } 'MVP-2 route key'
Assert-UniqueKey $stockpilotMvp2Policies { param($row) "$($row.store_id)|$($row.sku_id)|$($row.input_snapshot_version)" } 'MVP-2 policy key'

$stockpilotMvp2ProductKeys = New-StockPilotKeyMap $stockpilotMvp2Products 'sku_id'
$stockpilotMvp2StoreKeys = New-StockPilotKeyMap $stockpilotMvp2Stores 'store_id'
$stockpilotExpectedVersion = 'MVP-2-GS-V1'
$stockpilotExpectedObservationDates = @(
    0..27 | ForEach-Object { (Get-Date '2026-09-02').AddDays($_).ToString('yyyy-MM-dd') }
)
$stockpilotExpectedInventoryDates = @($stockpilotExpectedObservationDates + '2026-09-30')

foreach ($stockpilotRow in $stockpilotMvp2Products) {
    $null = ConvertTo-StockPilotDate $stockpilotRow.launch_date "launch_date for $($stockpilotRow.sku_id)"
    if ($stockpilotRow.sales_status -notin @('PRELAUNCH', 'ACTIVE', 'CLEARANCE', 'ENDED')) {
        throw "Invalid sales_status: $($stockpilotRow.sales_status)"
    }
}

foreach ($stockpilotRow in $stockpilotMvp2Stores) {
    if ($stockpilotRow.store_type -notin @('DIRECT', 'CONSIGNMENT', 'OTHER')) {
        throw "Invalid store_type: $($stockpilotRow.store_type)"
    }
    if ($stockpilotRow.transfer_zone -ne 'DOMESTIC') {
        throw "MVP-2 routes may only target domestic stores: $($stockpilotRow.store_id)"
    }
    if ([string]::IsNullOrWhiteSpace($stockpilotRow.inventory_owner_code)) {
        throw "Missing inventory_owner_code: $($stockpilotRow.store_id)"
    }
}

foreach ($stockpilotRow in $stockpilotMvp2Inventory) {
    if (-not $stockpilotMvp2ProductKeys.ContainsKey($stockpilotRow.sku_id)) { throw "Unknown MVP-2 inventory sku_id: $($stockpilotRow.sku_id)" }
    if (-not $stockpilotMvp2StoreKeys.ContainsKey($stockpilotRow.store_id)) { throw "Unknown MVP-2 inventory store_id: $($stockpilotRow.store_id)" }
    $stockpilotDate = ConvertTo-StockPilotDate $stockpilotRow.snapshot_date 'MVP-2 snapshot_date'
    $stockpilotTimestamp = ConvertTo-StockPilotTimestamp $stockpilotRow.snapshot_at 'MVP-2 snapshot_at'
    if ($stockpilotTimestamp.ToString('yyyy-MM-dd') -ne $stockpilotDate.ToString('yyyy-MM-dd')) {
        throw "snapshot_at date does not match snapshot_date: $($stockpilotRow.store_id)|$($stockpilotRow.sku_id)"
    }
    $stockpilotOnHand = ConvertTo-StockPilotInteger $stockpilotRow.on_hand_quantity 'MVP-2 on_hand_quantity'
    $stockpilotReserved = ConvertTo-StockPilotInteger $stockpilotRow.reserved_quantity 'MVP-2 reserved_quantity'
    if ($stockpilotReserved -gt $stockpilotOnHand) { throw "MVP-2 reserved quantity exceeds on-hand: $($stockpilotRow.store_id)|$($stockpilotRow.sku_id)" }
    $stockpilotExpectedOos = if (($stockpilotOnHand - $stockpilotReserved) -eq 0) { 'Y' } else { 'N' }
    if ($stockpilotRow.out_of_stock_flag -ne $stockpilotExpectedOos) {
        throw "out_of_stock_flag does not match available inventory: $($stockpilotRow.store_id)|$($stockpilotRow.sku_id)|$($stockpilotRow.snapshot_date)"
    }
    if ($stockpilotRow.input_snapshot_version -ne $stockpilotExpectedVersion -or $stockpilotRow.source_type -ne 'SYNTHETIC') {
        throw 'MVP-2 inventory must be SYNTHETIC and use MVP-2-GS-V1.'
    }
    if ($stockpilotRow.snapshot_date -notin $stockpilotExpectedInventoryDates) {
        throw "Unexpected MVP-2 inventory date: $($stockpilotRow.snapshot_date)"
    }
}

foreach ($stockpilotRow in $stockpilotMvp2Sales) {
    if (-not $stockpilotMvp2ProductKeys.ContainsKey($stockpilotRow.sku_id)) { throw "Unknown MVP-2 sales sku_id: $($stockpilotRow.sku_id)" }
    if (-not $stockpilotMvp2StoreKeys.ContainsKey($stockpilotRow.store_id)) { throw "Unknown MVP-2 sales store_id: $($stockpilotRow.store_id)" }
    $null = ConvertTo-StockPilotDate $stockpilotRow.sales_date 'MVP-2 sales_date'
    $stockpilotSold = ConvertTo-StockPilotInteger $stockpilotRow.sold_quantity 'MVP-2 sold_quantity'
    $stockpilotTransactions = ConvertTo-StockPilotInteger $stockpilotRow.transaction_count 'MVP-2 transaction_count'
    $stockpilotMaximumTransaction = ConvertTo-StockPilotInteger $stockpilotRow.max_transaction_quantity 'MVP-2 max_transaction_quantity'
    $null = ConvertTo-StockPilotDecimal $stockpilotRow.average_selling_price 'MVP-2 average_selling_price'
    if ($stockpilotSold -eq 0) {
        if ($stockpilotTransactions -ne 0 -or $stockpilotMaximumTransaction -ne 0) {
            throw 'Zero sales must have zero transactions and max transaction quantity.'
        }
    }
    elseif (
        $stockpilotTransactions -le 0 -or
        $stockpilotMaximumTransaction -le 0 -or
        $stockpilotTransactions -gt $stockpilotSold -or
        $stockpilotMaximumTransaction -gt $stockpilotSold
    ) {
        throw "Invalid transaction detail: $($stockpilotRow.store_id)|$($stockpilotRow.sku_id)|$($stockpilotRow.sales_date)"
    }
    if ($stockpilotRow.input_snapshot_version -ne $stockpilotExpectedVersion -or $stockpilotRow.source_type -ne 'SYNTHETIC') {
        throw 'MVP-2 sales must be SYNTHETIC and use MVP-2-GS-V1.'
    }
    if ($stockpilotRow.sales_date -notin $stockpilotExpectedObservationDates) {
        throw "Unexpected MVP-2 sales date: $($stockpilotRow.sales_date)"
    }
}

$stockpilotInventoryGroups = @($stockpilotMvp2Inventory | Group-Object -Property { "$($_.store_id)|$($_.sku_id)" })
$stockpilotSalesGroups = @($stockpilotMvp2Sales | Group-Object -Property { "$($_.store_id)|$($_.sku_id)" })
if ($stockpilotInventoryGroups.Count -ne 12 -or $stockpilotSalesGroups.Count -ne 12) {
    throw 'MVP-2 must contain exactly 12 store-SKU observation groups.'
}
foreach ($stockpilotGroup in $stockpilotInventoryGroups) {
    if ($stockpilotGroup.Count -ne 29) { throw "Inventory group must contain 29 dates: $($stockpilotGroup.Name)" }
    $stockpilotActualDates = @($stockpilotGroup.Group.snapshot_date | Sort-Object -Unique)
    if (($stockpilotActualDates -join ',') -ne (($stockpilotExpectedInventoryDates | Sort-Object) -join ',')) {
        throw "Inventory group date range is incomplete: $($stockpilotGroup.Name)"
    }
}
foreach ($stockpilotGroup in $stockpilotSalesGroups) {
    if ($stockpilotGroup.Count -ne 28) { throw "Sales group must contain 28 dates: $($stockpilotGroup.Name)" }
    $stockpilotActualDates = @($stockpilotGroup.Group.sales_date | Sort-Object -Unique)
    if (($stockpilotActualDates -join ',') -ne (($stockpilotExpectedObservationDates | Sort-Object) -join ',')) {
        throw "Sales group date range is incomplete: $($stockpilotGroup.Name)"
    }
}

foreach ($stockpilotRow in $stockpilotMvp2Events) {
    if (-not $stockpilotMvp2ProductKeys.ContainsKey($stockpilotRow.sku_id) -or -not $stockpilotMvp2StoreKeys.ContainsKey($stockpilotRow.store_id)) {
        throw "Invalid event reference: $($stockpilotRow.event_code)"
    }
    $stockpilotStart = ConvertTo-StockPilotDate $stockpilotRow.start_date 'event start_date'
    $stockpilotEnd = ConvertTo-StockPilotDate $stockpilotRow.end_date 'event end_date'
    if ($stockpilotStart -gt $stockpilotEnd) { throw "Event start_date exceeds end_date: $($stockpilotRow.event_code)" }
    $stockpilotUplifts = @($stockpilotRow.uplift_low, $stockpilotRow.uplift_base, $stockpilotRow.uplift_high)
    $stockpilotPresentUplifts = @($stockpilotUplifts | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    if ($stockpilotPresentUplifts.Count -notin @(0, 3)) { throw "Event uplift must provide all low/base/high values or none: $($stockpilotRow.event_code)" }
    if ($stockpilotPresentUplifts.Count -eq 3) {
        $stockpilotLow = ConvertTo-StockPilotDecimal $stockpilotRow.uplift_low 'uplift_low' -Positive
        $stockpilotBase = ConvertTo-StockPilotDecimal $stockpilotRow.uplift_base 'uplift_base' -Positive
        $stockpilotHigh = ConvertTo-StockPilotDecimal $stockpilotRow.uplift_high 'uplift_high' -Positive
        if ($stockpilotLow -gt $stockpilotBase -or $stockpilotBase -gt $stockpilotHigh) {
            throw "Event uplift must satisfy low <= base <= high: $($stockpilotRow.event_code)"
        }
    }
    if ($stockpilotRow.input_snapshot_version -ne $stockpilotExpectedVersion -or $stockpilotRow.source_type -ne 'SYNTHETIC' -or $stockpilotRow.assumption_type -ne 'ASSUMPTION') {
        throw 'MVP-2 events must be versioned SYNTHETIC/ASSUMPTION inputs.'
    }
}

foreach ($stockpilotRow in $stockpilotMvp2Inbound) {
    if (-not $stockpilotMvp2ProductKeys.ContainsKey($stockpilotRow.sku_id) -or -not $stockpilotMvp2StoreKeys.ContainsKey($stockpilotRow.store_id)) {
        throw "Invalid inbound reference: $($stockpilotRow.inbound_reference)"
    }
    $null = ConvertTo-StockPilotInteger $stockpilotRow.quantity 'inbound quantity' -Positive
    $null = ConvertTo-StockPilotTimestamp $stockpilotRow.eta_at 'inbound eta_at'
    if ($stockpilotRow.inbound_status -notin @('PLANNED', 'CONFIRMED', 'CANCELLED', 'RECEIVED')) { throw "Invalid inbound_status: $($stockpilotRow.inbound_status)" }
    if ($stockpilotRow.input_snapshot_version -ne $stockpilotExpectedVersion -or $stockpilotRow.source_type -ne 'SYNTHETIC') { throw 'MVP-2 inbound must be versioned SYNTHETIC data.' }
}

foreach ($stockpilotRow in $stockpilotMvp2OpenTransfers) {
    if (
        -not $stockpilotMvp2ProductKeys.ContainsKey($stockpilotRow.sku_id) -or
        -not $stockpilotMvp2StoreKeys.ContainsKey($stockpilotRow.donor_store_id) -or
        -not $stockpilotMvp2StoreKeys.ContainsKey($stockpilotRow.receiver_store_id)
    ) {
        throw "Invalid open transfer reference: $($stockpilotRow.transfer_reference)"
    }
    if ($stockpilotRow.donor_store_id -eq $stockpilotRow.receiver_store_id) { throw 'Open transfer donor and receiver must differ.' }
    $null = ConvertTo-StockPilotInteger $stockpilotRow.quantity 'open transfer quantity' -Positive
    $null = ConvertTo-StockPilotTimestamp $stockpilotRow.eta_at 'open transfer eta_at'
    if ($stockpilotRow.transfer_status -notin @('REQUESTED', 'APPROVED', 'IN_TRANSIT', 'CANCELLED', 'RECEIVED')) { throw "Invalid transfer_status: $($stockpilotRow.transfer_status)" }
    if ($stockpilotRow.input_snapshot_version -ne $stockpilotExpectedVersion -or $stockpilotRow.source_type -ne 'SYNTHETIC') { throw 'MVP-2 open transfers must be versioned SYNTHETIC data.' }
}

foreach ($stockpilotRow in $stockpilotMvp2Routes) {
    if (
        -not $stockpilotMvp2StoreKeys.ContainsKey($stockpilotRow.donor_store_id) -or
        -not $stockpilotMvp2StoreKeys.ContainsKey($stockpilotRow.receiver_store_id)
    ) {
        throw "Invalid route reference: $($stockpilotRow.donor_store_id)|$($stockpilotRow.receiver_store_id)"
    }
    if ($stockpilotRow.donor_store_id -eq $stockpilotRow.receiver_store_id) { throw 'Route donor and receiver must differ.' }
    if ($stockpilotRow.active_flag -notin @('Y', 'N') -or $stockpilotRow.owner_override_flag -notin @('Y', 'N')) { throw 'Route flags must be Y or N.' }
    $stockpilotLeadTime = ConvertTo-StockPilotInteger $stockpilotRow.lead_time_days 'lead_time_days'
    $stockpilotMinimum = ConvertTo-StockPilotInteger $stockpilotRow.minimum_quantity 'minimum_quantity' -Positive
    $stockpilotMultiple = ConvertTo-StockPilotInteger $stockpilotRow.package_multiple 'package_multiple' -Positive
    $stockpilotMaximum = ConvertTo-StockPilotInteger $stockpilotRow.maximum_quantity 'maximum_quantity' -Positive
    if ($stockpilotMaximum -lt $stockpilotMinimum) { throw 'Route maximum_quantity must be at least minimum_quantity.' }
    if ($stockpilotRow.input_snapshot_version -ne $stockpilotExpectedVersion -or $stockpilotRow.assumption_type -ne 'ASSUMPTION') { throw 'MVP-2 routes must be versioned ASSUMPTION data.' }
    $stockpilotDonor = $stockpilotMvp2StoreKeys[$stockpilotRow.donor_store_id]
    $stockpilotReceiver = $stockpilotMvp2StoreKeys[$stockpilotRow.receiver_store_id]
    if ($stockpilotDonor.transfer_zone -ne 'DOMESTIC' -or $stockpilotReceiver.transfer_zone -ne 'DOMESTIC') {
        throw 'MVP-2 routes are limited to domestic stores.'
    }
}

foreach ($stockpilotRow in $stockpilotMvp2Policies) {
    if (-not $stockpilotMvp2ProductKeys.ContainsKey($stockpilotRow.sku_id) -or -not $stockpilotMvp2StoreKeys.ContainsKey($stockpilotRow.store_id)) {
        throw "Invalid store-SKU policy reference: $($stockpilotRow.store_id)|$($stockpilotRow.sku_id)"
    }
    $stockpilotDisplay = ConvertTo-StockPilotInteger $stockpilotRow.display_minimum 'display_minimum'
    $stockpilotSafety = ConvertTo-StockPilotInteger $stockpilotRow.safety_stock 'safety_stock'
    $stockpilotCapacity = ConvertTo-StockPilotInteger $stockpilotRow.maximum_capacity 'maximum_capacity' -Positive
    $null = ConvertTo-StockPilotInteger $stockpilotRow.target_coverage_days 'target_coverage_days'
    $null = ConvertTo-StockPilotInteger $stockpilotRow.retained_days 'retained_days'
    if (($stockpilotDisplay + $stockpilotSafety) -gt $stockpilotCapacity) { throw 'Policy display minimum plus safety stock exceeds capacity.' }
    if ($stockpilotRow.input_snapshot_version -ne $stockpilotExpectedVersion -or $stockpilotRow.assumption_type -ne 'ASSUMPTION') { throw 'MVP-2 store-SKU policies must be versioned ASSUMPTION data.' }
}

# GS-01: 28 days of stable receiver demand.
$stockpilotGs01Sales = @($stockpilotMvp2Sales | Where-Object {
    $_.store_id -eq 'STORE-MVP2-RECEIVER-A' -and $_.sku_id -eq 'SKU-MVP2-GS01-STABLE'
})
if ($stockpilotGs01Sales.Count -ne 28 -or @($stockpilotGs01Sales | Where-Object sold_quantity -ne '2').Count -ne 0) {
    throw 'GS-01 must contain 28 receiver sales days with quantity 2.'
}

# GS-02: a known future event with complete input uplift, never system-predicted.
$stockpilotGs02Event = @($stockpilotMvp2Events | Where-Object event_code -eq 'EVENT-MVP2-GS02')
if (
    $stockpilotGs02Event.Count -ne 1 -or
    $stockpilotGs02Event[0].uplift_low -ne '1.20' -or
    $stockpilotGs02Event[0].uplift_base -ne '1.50' -or
    $stockpilotGs02Event[0].uplift_high -ne '1.80'
) {
    throw 'GS-02 event uplift input does not match low/base/high contract.'
}

# GS-03: one transaction accounts for the full-window spike.
$stockpilotGs03Sales = @($stockpilotMvp2Sales | Where-Object {
    $_.store_id -eq 'STORE-MVP2-RECEIVER-A' -and $_.sku_id -eq 'SKU-MVP2-GS03-SPIKE'
})
$stockpilotGs03Positive = @($stockpilotGs03Sales | Where-Object { [int]$_.sold_quantity -gt 0 })
$stockpilotGs03Total = ($stockpilotGs03Sales | Measure-Object -Property sold_quantity -Sum).Sum
if (
    $stockpilotGs03Positive.Count -ne 1 -or
    [int]$stockpilotGs03Total -ne 20 -or
    $stockpilotGs03Positive[0].transaction_count -ne '1' -or
    $stockpilotGs03Positive[0].max_transaction_quantity -ne '20'
) {
    throw 'GS-03 must contain one 20-unit transaction and no other receiver sales.'
}

# GS-04: exactly 14 censored OOS days, followed by 14 observable sales days.
$stockpilotGs04Observation = @($stockpilotMvp2Inventory | Where-Object {
    $_.snapshot_date -in $stockpilotExpectedObservationDates -and
    $_.store_id -eq 'STORE-MVP2-RECEIVER-A' -and
    $_.sku_id -eq 'SKU-MVP2-GS04-OOS'
})
$stockpilotGs04OosDates = @($stockpilotGs04Observation | Where-Object out_of_stock_flag -eq 'Y' | Select-Object -ExpandProperty snapshot_date)
$stockpilotGs04OosSales = @($stockpilotMvp2Sales | Where-Object {
    $_.sales_date -in $stockpilotGs04OosDates -and
    $_.store_id -eq 'STORE-MVP2-RECEIVER-A' -and
    $_.sku_id -eq 'SKU-MVP2-GS04-OOS' -and
    $_.sold_quantity -ne '0'
})
if ($stockpilotGs04OosDates.Count -ne 14 -or $stockpilotGs04OosSales.Count -ne 0) {
    throw 'GS-04 must have exactly 14 OOS-censored zero-sales days.'
}

# GS-05: a confirmed inbound input covers the receiver shortage scenario.
$stockpilotGs05Inbound = @($stockpilotMvp2Inbound | Where-Object inbound_reference -eq 'INBOUND-MVP2-GS05')
if (
    $stockpilotGs05Inbound.Count -ne 1 -or
    $stockpilotGs05Inbound[0].inbound_status -ne 'CONFIRMED' -or
    [int]$stockpilotGs05Inbound[0].quantity -ne 50
) {
    throw 'GS-05 confirmed inbound does not match the contract.'
}

# GS-06: domestic stores have different owners and no explicit owner override.
$stockpilotGs06Route = @($stockpilotMvp2Routes | Where-Object {
    $_.donor_store_id -eq 'STORE-MVP2-DONOR-B' -and
    $_.receiver_store_id -eq 'STORE-MVP2-RECEIVER-A'
})
if (
    $stockpilotGs06Route.Count -ne 1 -or
    $stockpilotGs06Route[0].active_flag -ne 'Y' -or
    $stockpilotGs06Route[0].owner_override_flag -ne 'N' -or
    [int]$stockpilotGs06Route[0].lead_time_days -ne 10 -or
    $stockpilotMvp2StoreKeys['STORE-MVP2-DONOR-B'].inventory_owner_code -eq $stockpilotMvp2StoreKeys['STORE-MVP2-RECEIVER-A'].inventory_owner_code
) {
    throw 'GS-06 must reproduce owner mismatch and long lead-time rejection inputs.'
}

Write-Output 'Seed validation passed.'
Write-Output "MVP-1: Products=$($stockpilotProducts.Count), Stores=$($stockpilotStores.Count), Inventory=$($stockpilotInventory.Count), Sales=$($stockpilotSales.Count)"
Write-Output "MVP-2: Products=$($stockpilotMvp2Products.Count), Stores=$($stockpilotMvp2Stores.Count), Inventory=$($stockpilotMvp2Inventory.Count), Sales=$($stockpilotMvp2Sales.Count), Scenarios=6"

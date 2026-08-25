[CmdletBinding()]
param(
    [string]$SeedDirectory = (Join-Path $PSScriptRoot '..\data\seed')
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$stockpilotSeedDirectory = [System.IO.Path]::GetFullPath($SeedDirectory)

function Import-StockPilotCsv {
    param(
        [string]$FileName,
        [string[]]$ExpectedHeaders
    )

    $stockpilotPath = Join-Path $stockpilotSeedDirectory $FileName
    if (-not (Test-Path -LiteralPath $stockpilotPath)) {
        throw "Missing Seed file: $FileName"
    }

    $stockpilotHeader = (Get-Content -LiteralPath $stockpilotPath -TotalCount 1).Split(',')
    if (Compare-Object $ExpectedHeaders $stockpilotHeader) {
        throw "Unexpected headers in ${FileName}. Expected: $($ExpectedHeaders -join ',')"
    }

    $stockpilotRows = @(Import-Csv -LiteralPath $stockpilotPath)
    if ($stockpilotRows.Count -eq 0) {
        throw "Seed file has no rows: $FileName"
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
        throw "Duplicate ${Label}: $($stockpilotDuplicates.Name -join ', ')"
    }
}

$stockpilotProducts = @(Import-StockPilotCsv 'products.csv' @('sku_id', 'product_name', 'category', 'color', 'size'))
$stockpilotStores = @(Import-StockPilotCsv 'stores.csv' @('store_id', 'store_name', 'region'))
$stockpilotInventory = @(Import-StockPilotCsv 'inventory.csv' @('snapshot_date', 'store_id', 'sku_id', 'on_hand_quantity', 'reserved_quantity'))
$stockpilotSales = @(Import-StockPilotCsv 'sales.csv' @('sales_date', 'store_id', 'sku_id', 'sold_quantity'))

Assert-UniqueKey $stockpilotProducts { param($row) $row.sku_id } 'sku_id'
Assert-UniqueKey $stockpilotStores { param($row) $row.store_id } 'store_id'
Assert-UniqueKey $stockpilotInventory { param($row) "$($row.snapshot_date)|$($row.store_id)|$($row.sku_id)" } 'inventory key'
Assert-UniqueKey $stockpilotSales { param($row) "$($row.sales_date)|$($row.store_id)|$($row.sku_id)" } 'sales key'

$stockpilotProductKeys = @{}
foreach ($stockpilotProduct in $stockpilotProducts) { $stockpilotProductKeys[$stockpilotProduct.sku_id] = $true }
$stockpilotStoreKeys = @{}
foreach ($stockpilotStore in $stockpilotStores) { $stockpilotStoreKeys[$stockpilotStore.store_id] = $true }

foreach ($stockpilotRow in $stockpilotInventory) {
    if (-not $stockpilotProductKeys.ContainsKey($stockpilotRow.sku_id)) { throw "Unknown inventory sku_id: $($stockpilotRow.sku_id)" }
    if (-not $stockpilotStoreKeys.ContainsKey($stockpilotRow.store_id)) { throw "Unknown inventory store_id: $($stockpilotRow.store_id)" }
    if ($stockpilotRow.on_hand_quantity -notmatch '^\d+$' -or $stockpilotRow.reserved_quantity -notmatch '^\d+$') { throw 'Inventory quantities must be non-negative integers.' }
    if ([int]$stockpilotRow.reserved_quantity -gt [int]$stockpilotRow.on_hand_quantity) { throw "Reserved quantity exceeds on-hand quantity: $($stockpilotRow.store_id)" }
}

foreach ($stockpilotRow in $stockpilotSales) {
    if (-not $stockpilotProductKeys.ContainsKey($stockpilotRow.sku_id)) { throw "Unknown sales sku_id: $($stockpilotRow.sku_id)" }
    if (-not $stockpilotStoreKeys.ContainsKey($stockpilotRow.store_id)) { throw "Unknown sales store_id: $($stockpilotRow.store_id)" }
    if ($stockpilotRow.sold_quantity -notmatch '^\d+$') { throw 'Sold quantity must be a non-negative integer.' }
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

Write-Output 'Seed validation passed.'
Write-Output "Products=$($stockpilotProducts.Count), Stores=$($stockpilotStores.Count), Inventory=$($stockpilotInventory.Count), Sales=$($stockpilotSales.Count)"

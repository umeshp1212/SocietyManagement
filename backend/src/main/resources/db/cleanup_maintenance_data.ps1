# =============================================================================
# MAINTENANCE DATA CLEANUP SCRIPT FOR WINDOWS
# =============================================================================
# PowerShell script to safely clean up maintenance test data from production
# =============================================================================

param(
    [Parameter(Mandatory=$true)]
    [string]$DatabaseHost = "localhost",
    
    [Parameter(Mandatory=$true)]
    [string]$DatabaseName = "society_management",
    
    [Parameter(Mandatory=$true)]
    [string]$Username,
    
    [Parameter(Mandatory=$false)]
    [switch]$CreateBackup = $true,
    
    [Parameter(Mandatory=$false)]
    [switch]$SkipConfirmation = $false
)

# Function to execute SQL file
function Execute-SqlFile {
    param(
        [string]$SqlFile,
        [string]$Description
    )
    
    Write-Host "📂 Executing: $Description" -ForegroundColor Yellow
    Write-Host "   File: $SqlFile" -ForegroundColor Gray
    
    try {
        mysql -h $DatabaseHost -u $Username -p $DatabaseName < $SqlFile
        
        if ($LASTEXITCODE -eq 0) {
            Write-Host "✅ $Description completed successfully!" -ForegroundColor Green
        } else {
            Write-Host "❌ $Description failed!" -ForegroundColor Red
            exit 1
        }
    }
    catch {
        Write-Host "❌ Error executing $Description : $_" -ForegroundColor Red
        exit 1
    }
}

# Function to show database info
function Show-DatabaseInfo {
    Write-Host "🔍 Database Information:" -ForegroundColor Cyan
    Write-Host "   Host: $DatabaseHost" -ForegroundColor White
    Write-Host "   Database: $DatabaseName" -ForegroundColor White
    Write-Host "   Username: $Username" -ForegroundColor White
    Write-Host ""
}

# Main script
Clear-Host
Write-Host "🗂️  SOCIETY MANAGEMENT - MAINTENANCE DATA CLEANUP" -ForegroundColor Magenta
Write-Host "=" * 60 -ForegroundColor Magenta
Write-Host ""

Show-DatabaseInfo

# Check if MySQL is available
try {
    mysql --version | Out-Null
    Write-Host "✅ MySQL client found" -ForegroundColor Green
}
catch {
    Write-Host "❌ MySQL client not found! Please install MySQL client." -ForegroundColor Red
    exit 1
}

# Warning message
Write-Host "⚠️  WARNING: This will permanently delete ALL maintenance test data!" -ForegroundColor Red
Write-Host "   - All maintenance bills (generated bills)" -ForegroundColor Yellow
Write-Host "   - All outstanding bills" -ForegroundColor Yellow
Write-Host "   - All payment history" -ForegroundColor Yellow  
Write-Host "   - All maintenance-related transactions" -ForegroundColor Yellow
Write-Host "   - Receipt numbering will restart from 1" -ForegroundColor Yellow
Write-Host ""
Write-Host "✅ WILL BE PRESERVED:" -ForegroundColor Green
Write-Host "   - Units and owners data" -ForegroundColor White
Write-Host "   - User accounts and roles" -ForegroundColor White
Write-Host "   - Maintenance configuration" -ForegroundColor White
Write-Host "   - All other modules (committee, vendor, etc.)" -ForegroundColor White
Write-Host ""

# Get confirmation unless skipped
if (-not $SkipConfirmation) {
    $confirmation = Read-Host "Are you absolutely sure you want to proceed? Type 'DELETE TEST DATA' to confirm"
    
    if ($confirmation -ne "DELETE TEST DATA") {
        Write-Host "❌ Operation cancelled. Confirmation text did not match." -ForegroundColor Red
        exit 0
    }
}

Write-Host ""
Write-Host "🚀 Starting maintenance data cleanup process..." -ForegroundColor Green
Write-Host ""

# Step 1: Create backup if requested
if ($CreateBackup) {
    Write-Host "📋 Step 1: Creating backup..." -ForegroundColor Cyan
    Execute-SqlFile "create_maintenance_backup.sql" "Backup Creation"
    Write-Host ""
}

# Step 2: Execute cleanup
Write-Host "🧹 Step 2: Cleaning up maintenance data..." -ForegroundColor Cyan
Execute-SqlFile "quick_maintenance_cleanup.sql" "Maintenance Data Cleanup"
Write-Host ""

# Step 3: Final verification
Write-Host "🔍 Step 3: Final verification..." -ForegroundColor Cyan
Write-Host "Connecting to database to verify cleanup..." -ForegroundColor Gray

$verificationQuery = @"
SELECT 'FINAL VERIFICATION' as status;
SELECT 'maintenance_bills' AS table_name, COUNT(*) AS remaining_rows FROM maintenance_bills
UNION ALL SELECT 'maintenance_payments', COUNT(*) FROM maintenance_payments  
UNION ALL SELECT 'Total maintenance records', 
    (SELECT COUNT(*) FROM maintenance_bills) + 
    (SELECT COUNT(*) FROM maintenance_payments) + 
    (SELECT COUNT(*) FROM maintenance_ledger) AS total_remaining;
"@

echo $verificationQuery | mysql -h $DatabaseHost -u $Username -p $DatabaseName

Write-Host ""
Write-Host "🎉 MAINTENANCE DATA CLEANUP COMPLETED!" -ForegroundColor Green
Write-Host "=" * 50 -ForegroundColor Green
Write-Host ""
Write-Host "✅ Next steps:" -ForegroundColor Cyan
Write-Host "   1. Verify your application still works correctly" -ForegroundColor White
Write-Host "   2. Test maintenance bill generation" -ForegroundColor White
Write-Host "   3. Monitor for any issues" -ForegroundColor White

if ($CreateBackup) {
    Write-Host "   4. Clean up backup tables when confident (see backup script)" -ForegroundColor White
}

Write-Host ""
Write-Host "📞 If you need to restore data, use: restore_maintenance_backup.sql" -ForegroundColor Yellow
Write-Host "🗂️  All scripts are in: backend/src/main/resources/db/" -ForegroundColor Gray
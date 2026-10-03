# Maintenance Data Cleanup Scripts

This directory contains scripts to safely delete all test maintenance data from your production database while preserving critical configuration and user data.

## 🎯 What Gets Deleted

- ✅ All maintenance bills (generated bills for all months)
- ✅ All outstanding bills  
- ✅ All payment history and transactions
- ✅ All maintenance ledger entries (audit trail)
- ✅ All unit advance credits (prepaid amounts)
- ✅ All suspense entries (unidentified payments)
- ✅ All penalties
- ✅ All opening balances
- ✅ Receipt number sequences (will restart from RCP-YYYYMM-00001)

## 🛡️ What Gets Preserved

- ✅ Units and unit configuration
- ✅ Owners and owner details
- ✅ User accounts, roles, and authentication
- ✅ Maintenance charge configuration
- ✅ Water charge configuration
- ✅ All other modules (committee, vendor, NOC, TDS, etc.)

## 📁 Available Scripts

### 1. `production_cleanup_maintenance_data.sql` (Recommended)
**The safest option with maximum control**
- Includes safety checks and database name verification
- Runs in a transaction (can rollback if needed)
- Shows before/after counts
- Requires manual commit/rollback decision

### 2. `quick_maintenance_cleanup.sql` 
**Direct execution without modifications**
- Simplified version for quick execution
- Shows before/after counts
- No transaction wrapper (immediate execution)

### 3. `create_maintenance_backup.sql`
**Create backup before cleanup**
- Creates timestamped backup tables
- Allows restoration if needed
- Run this BEFORE cleanup scripts

### 4. `restore_maintenance_backup.sql`
**Restore from backup if needed**
- Restores data from backup tables
- Modify the timestamp in the script
- Use if cleanup went wrong

### 5. `cleanup_maintenance_data.ps1` (Windows PowerShell)
**Complete automated solution**
- Handles entire process automatically
- Creates backups by default
- Includes verification steps
- Best for Windows environments

## 🚀 Usage Instructions

### Option A: Using PowerShell Script (Recommended for Windows)

```powershell
# Navigate to the db directory
cd "d:\Tutorial\SocietyManagement\backend\src\main\resources\db"

# Run the PowerShell script
.\cleanup_maintenance_data.ps1 -DatabaseHost "localhost" -DatabaseName "society_management" -Username "your_username"

# Skip backup (not recommended)
.\cleanup_maintenance_data.ps1 -DatabaseHost "localhost" -DatabaseName "society_management" -Username "your_username" -CreateBackup:$false

# Skip confirmation (be very careful!)
.\cleanup_maintenance_data.ps1 -DatabaseHost "localhost" -DatabaseName "society_management" -Username "your_username" -SkipConfirmation
```

### Option B: Manual SQL Execution

1. **Create a backup first (IMPORTANT!):**
   ```sql
   mysql -u your_username -p your_database < create_maintenance_backup.sql
   ```

2. **Run the cleanup:**
   ```sql
   -- For maximum safety (with transaction):
   mysql -u your_username -p your_database < production_cleanup_maintenance_data.sql
   
   -- OR for quick execution:
   mysql -u your_username -p your_database < quick_maintenance_cleanup.sql
   ```

3. **If something goes wrong, restore:**
   ```sql
   -- Edit restore_maintenance_backup.sql to set correct timestamp first
   mysql -u your_username -p your_database < restore_maintenance_backup.sql
   ```

## ⚠️ Pre-Execution Checklist

- [ ] **Take a complete database backup**
- [ ] **Stop all applications using the database** 
- [ ] **Ensure no users are actively using the system**
- [ ] **Verify you're connected to the correct database**
- [ ] **Have a rollback plan ready**
- [ ] **Test the restore process on a copy first**

## 🔍 Post-Execution Verification

1. **Check that critical data is preserved:**
   ```sql
   SELECT COUNT(*) as units FROM units;
   SELECT COUNT(*) as owners FROM owners;
   SELECT COUNT(*) as users FROM users;
   ```

2. **Verify cleanup completed:**
   ```sql
   SELECT COUNT(*) as maintenance_bills FROM maintenance_bills;
   SELECT COUNT(*) as payments FROM maintenance_payments;
   ```

3. **Test application functionality:**
   - Login to the application
   - Navigate to maintenance module
   - Try generating a new bill
   - Verify receipt numbering starts fresh

## 🆘 Emergency Restoration

If you need to restore immediately:

1. **Find your backup timestamp:**
   ```sql
   SHOW TABLES LIKE '%backup_%';
   ```

2. **Edit `restore_maintenance_backup.sql`:**
   - Change the `@backup_suffix` variable to your timestamp
   
3. **Run the restore:**
   ```sql
   mysql -u your_username -p your_database < restore_maintenance_backup.sql
   ```

## 🧹 Cleanup Backup Tables

After confirming everything works correctly, remove backup tables:

```sql
-- List backup tables
SHOW TABLES LIKE '%backup_%';

-- Drop backup tables (replace with your timestamp)
DROP TABLE maintenance_bills_backup_20241003_143000;
DROP TABLE maintenance_payments_backup_20241003_143000;
-- ... (drop all backup tables)
```

## 🎯 Expected Results

After successful cleanup:
- All maintenance-related tables will be empty (0 rows)
- Receipt numbering will restart from RCP-YYYYMM-00001
- Units, owners, and configuration data will be unchanged
- Application should work normally for new maintenance data
- Old test data will be completely removed

## 📞 Support

If you encounter issues:
1. Check the error messages carefully
2. Verify database connectivity
3. Ensure proper permissions
4. Use the backup restoration if needed
5. Test on a database copy first if unsure

---

**⚠️ Remember: These scripts permanently delete financial data. Always test first and have backups ready!**
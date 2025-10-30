-- 🗑️ COMPLETE CLEANUP OF OLD TABLES
-- Hapus semua tabel lama yang sudah tidak digunakan
-- Jalankan script ini di Supabase SQL Editor

-- ========================================
-- 1. DROP OLD BARCODE TABLES
-- ========================================

-- Drop barcode_scan table (old)
DROP TABLE IF EXISTS barcode_scan CASCADE;

-- Drop barcode_session table (old)
DROP TABLE IF EXISTS barcode_session CASCADE;

-- Drop barcode_performance table (old)
DROP TABLE IF EXISTS barcode_performance CASCADE;

-- ========================================
-- 2. DROP OLD INDICES
-- ========================================

-- Drop old barcode indices
DROP INDEX IF EXISTS idx_barcode_scan_created;
DROP INDEX IF EXISTS idx_barcode_scan_resi;
DROP INDEX IF EXISTS idx_barcode_scan_resino;
DROP INDEX IF EXISTS idx_barcode_scan_orderno;
DROP INDEX IF EXISTS idx_barcode_scan_chanelsales;
DROP INDEX IF EXISTS idx_barcode_scan_datetrans;

DROP INDEX IF EXISTS idx_barcode_session_created;
DROP INDEX IF EXISTS idx_barcode_session_resino;
DROP INDEX IF EXISTS idx_barcode_session_orderno;
DROP INDEX IF EXISTS idx_barcode_session_chanelsales;
DROP INDEX IF EXISTS idx_barcode_session_couriername;
DROP INDEX IF EXISTS idx_barcode_session_datetrans;

-- ========================================
-- 3. DROP OLD POLICIES
-- ========================================

-- Drop old barcode policies
DROP POLICY IF EXISTS "Allow read access barcode_scan" ON barcode_scan;
DROP POLICY IF EXISTS "Allow insert access barcode_scan" ON barcode_scan;
DROP POLICY IF EXISTS "Allow update access barcode_scan" ON barcode_scan;
DROP POLICY IF EXISTS "Allow delete access barcode_scan" ON barcode_scan;

DROP POLICY IF EXISTS "Allow read access barcode_session" ON barcode_session;
DROP POLICY IF EXISTS "Allow insert access barcode_session" ON barcode_session;
DROP POLICY IF EXISTS "Allow update access barcode_session" ON barcode_session;
DROP POLICY IF EXISTS "Allow delete access barcode_session" ON barcode_session;

-- ========================================
-- 4. VERIFY CLEANUP
-- ========================================

-- Check if old tables are removed
SELECT 
    CASE 
        WHEN EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'barcode_scan') 
        THEN '❌ barcode_scan still exists'
        ELSE '✅ barcode_scan removed'
    END as barcode_scan_status,
    
    CASE 
        WHEN EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'barcode_session') 
        THEN '❌ barcode_session still exists'
        ELSE '✅ barcode_session removed'
    END as barcode_session_status,
    
    CASE 
        WHEN EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'barcode_performance') 
        THEN '❌ barcode_performance still exists'
        ELSE '✅ barcode_performance removed'
    END as barcode_performance_status;

-- List remaining tables
SELECT 
    'Remaining tables after cleanup:' as info,
    tablename
FROM pg_tables 
WHERE schemaname = 'public'
ORDER BY tablename;

SELECT 'Old tables cleanup completed successfully' as status;


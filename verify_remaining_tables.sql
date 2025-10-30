-- 🔍 VERIFY REMAINING TABLES AFTER CLEANUP
-- Script untuk memverifikasi tabel yang tersisa di Supabase
-- Jalankan script ini di Supabase SQL Editor setelah cleanup

-- 1. List semua tabel yang ada
SELECT 
    schemaname,
    tablename,
    tableowner
FROM pg_tables 
WHERE schemaname = 'public'
ORDER BY tablename;

-- 2. Verifikasi tabel yang seharusnya ada
SELECT 
    CASE 
        WHEN EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'tbl_resi') 
        THEN '✅ tbl_resi exists'
        ELSE '❌ tbl_resi missing'
    END as tbl_resi_status,
    
    CASE 
        WHEN EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'tbl_expedisi') 
        THEN '✅ tbl_expedisi exists'
        ELSE '❌ tbl_expedisi missing'
    END as tbl_expedisi_status,
    
    CASE 
        WHEN EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'picklist') 
        THEN '✅ picklist exists'
        ELSE '❌ picklist missing'
    END as picklist_status,
    
    CASE 
        WHEN EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'picklist_scan') 
        THEN '✅ picklist_scan exists'
        ELSE '❌ picklist_scan missing'
    END as picklist_scan_status;

-- 3. Verifikasi tabel lama sudah dihapus
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

-- 4. Count records di tabel yang tersisa
SELECT 
    'tbl_resi' as table_name,
    COUNT(*) as record_count
FROM tbl_resi
UNION ALL
SELECT 
    'tbl_expedisi' as table_name,
    COUNT(*) as record_count
FROM tbl_expedisi
UNION ALL
SELECT 
    'picklist' as table_name,
    COUNT(*) as record_count
FROM picklist
UNION ALL
SELECT 
    'picklist_scan' as table_name,
    COUNT(*) as record_count
FROM picklist_scan;

-- 5. Summary
SELECT 'Database cleanup verification completed' as status;


-- 🗑️ CLEANUP OLD BARCODE TABLES
-- Hapus tabel lama yang sudah tidak digunakan
-- Jalankan script ini di Supabase SQL Editor

-- 1. Drop tabel barcode_scan (tabel lama)
DROP TABLE IF EXISTS barcode_scan CASCADE;

-- 2. Drop tabel barcode_session (tabel lama)
DROP TABLE IF EXISTS barcode_session CASCADE;

-- 3. Drop tabel barcode_performance jika ada (tabel lama)
DROP TABLE IF EXISTS barcode_performance CASCADE;

-- 4. Drop indices yang terkait dengan tabel lama
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

-- 5. Drop policies yang terkait dengan tabel lama
DROP POLICY IF EXISTS "Allow read access barcode_scan" ON barcode_scan;
DROP POLICY IF EXISTS "Allow insert access barcode_scan" ON barcode_scan;
DROP POLICY IF EXISTS "Allow update access barcode_scan" ON barcode_scan;
DROP POLICY IF EXISTS "Allow delete access barcode_scan" ON barcode_scan;

DROP POLICY IF EXISTS "Allow read access barcode_session" ON barcode_session;
DROP POLICY IF EXISTS "Allow insert access barcode_session" ON barcode_session;
DROP POLICY IF EXISTS "Allow update access barcode_session" ON barcode_session;
DROP POLICY IF EXISTS "Allow delete access barcode_session" ON barcode_session;

-- 6. Verifikasi tabel yang tersisa
-- Tabel yang seharusnya ada setelah cleanup:
-- - tbl_resi (untuk barcode scan data)
-- - tbl_expedisi (untuk expedisi data dengan flag)
-- - picklist (untuk picklist data)
-- - picklist_scan (untuk RFID scan data)

-- 7. Log cleanup completion
SELECT 'Cleanup completed: Old barcode tables removed' as status;


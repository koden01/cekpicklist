-- 🗄️ DATABASE MIGRATION: Version 3 to Version 4
-- Standardisasi nama tabel dan kolom antara Room dan Supabase
-- Jalankan script ini untuk migrasi database

-- 1. Rename tabel barcode_resi ke barcode_scan
ALTER TABLE barcode_resi RENAME TO barcode_scan;

-- 2. Rename tabel barcode_expedisi ke barcode_session  
ALTER TABLE barcode_expedisi RENAME TO barcode_session;

-- 3. Add missing columns to barcode_scan (jika belum ada)
ALTER TABLE barcode_scan ADD COLUMN resino TEXT;
ALTER TABLE barcode_scan ADD COLUMN couriername TEXT;
ALTER TABLE barcode_scan ADD COLUMN flag TEXT DEFAULT 'NO';
ALTER TABLE barcode_scan ADD COLUMN cekfu BOOLEAN DEFAULT false;

-- 4. Add missing columns to barcode_session (jika belum ada)
ALTER TABLE barcode_session ADD COLUMN lastUpdated INTEGER DEFAULT 0;
ALTER TABLE barcode_session ADD COLUMN isSynced BOOLEAN DEFAULT true;

-- 5. Update indices untuk tabel baru
DROP INDEX IF EXISTS idx_barcode_resi_created;
DROP INDEX IF EXISTS idx_barcode_resi_resi;
DROP INDEX IF EXISTS idx_barcode_resi_orderno;
DROP INDEX IF EXISTS idx_barcode_resi_chanelsales;
DROP INDEX IF EXISTS idx_barcode_resi_datetrans;

DROP INDEX IF EXISTS idx_barcode_expedisi_created;
DROP INDEX IF EXISTS idx_barcode_expedisi_resino;
DROP INDEX IF EXISTS idx_barcode_expedisi_orderno;
DROP INDEX IF EXISTS idx_barcode_expedisi_chanelsales;
DROP INDEX IF EXISTS idx_barcode_expedisi_couriername;
DROP INDEX IF EXISTS idx_barcode_expedisi_datetrans;

-- 6. Create new indices untuk barcode_scan
CREATE INDEX IF NOT EXISTS idx_barcode_scan_created ON barcode_scan(created);
CREATE INDEX IF NOT EXISTS idx_barcode_scan_resi ON barcode_scan(Resi);
CREATE INDEX IF NOT EXISTS idx_barcode_scan_resino ON barcode_scan(resino);
CREATE INDEX IF NOT EXISTS idx_barcode_scan_orderno ON barcode_scan(orderno);
CREATE INDEX IF NOT EXISTS idx_barcode_scan_chanelsales ON barcode_scan(chanelsales);
CREATE INDEX IF NOT EXISTS idx_barcode_scan_datetrans ON barcode_scan(datetrans);
CREATE INDEX IF NOT EXISTS idx_barcode_scan_scanner_type ON barcode_scan(scannerType);
CREATE INDEX IF NOT EXISTS idx_barcode_scan_status ON barcode_scan(status);
CREATE INDEX IF NOT EXISTS idx_barcode_scan_flag ON barcode_scan(flag);
CREATE INDEX IF NOT EXISTS idx_barcode_scan_schedule ON barcode_scan(schedule);

-- 7. Create new indices untuk barcode_session
CREATE INDEX IF NOT EXISTS idx_barcode_session_created ON barcode_session(created);
CREATE INDEX IF NOT EXISTS idx_barcode_session_resino ON barcode_session(resino);
CREATE INDEX IF NOT EXISTS idx_barcode_session_orderno ON barcode_session(orderno);
CREATE INDEX IF NOT EXISTS idx_barcode_session_chanelsales ON barcode_session(chanelsales);
CREATE INDEX IF NOT EXISTS idx_barcode_session_couriername ON barcode_session(couriername);
CREATE INDEX IF NOT EXISTS idx_barcode_session_datetrans ON barcode_session(datetrans);
CREATE INDEX IF NOT EXISTS idx_barcode_session_scanner_type ON barcode_session(scannerType);
CREATE INDEX IF NOT EXISTS idx_barcode_session_flag ON barcode_session(flag);

-- 8. Update existing data dengan default values
UPDATE barcode_scan SET flag = 'NO' WHERE flag IS NULL;
UPDATE barcode_scan SET cekfu = false WHERE cekfu IS NULL;
UPDATE barcode_scan SET resino = Resi WHERE resino IS NULL;
UPDATE barcode_scan SET couriername = 'Built-in Scanner' WHERE couriername IS NULL;

UPDATE barcode_session SET lastUpdated = 0 WHERE lastUpdated IS NULL;
UPDATE barcode_session SET isSynced = true WHERE isSynced IS NULL;

-- 9. Verify migration
SELECT 'barcode_scan' as table_name, COUNT(*) as record_count FROM barcode_scan
UNION ALL
SELECT 'barcode_session' as table_name, COUNT(*) as record_count FROM barcode_session;

-- ✅ Migration completed!
-- Database schema sekarang sudah sama antara Room dan Supabase

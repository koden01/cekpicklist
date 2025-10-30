-- 🗄️ DATABASE MIGRATION: Version 6 to Version 7
-- Force recreate database dengan schema yang benar
-- Jalankan script ini untuk migrasi database

-- 1. Drop semua tabel lama (jika ada)
DROP TABLE IF EXISTS barcode_scan;
DROP TABLE IF EXISTS barcode_session;
DROP TABLE IF EXISTS barcode_resi;
DROP TABLE IF EXISTS barcode_expedisi;

-- 2. Buat tabel tbl_resi (sesuai Supabase asli)
CREATE TABLE IF NOT EXISTS tbl_resi (
    created TEXT, -- Created timestamp
    Resi TEXT NOT NULL UNIQUE, -- Barcode value (Primary Key)
    Keterangan TEXT, -- Description/Scanner type
    nokarung TEXT, -- Karung number
    schedule TEXT, -- Schedule status: 'ontime', 'late', 'batal'
    CONSTRAINT tbl_resi_pkey PRIMARY KEY (Resi)
);

-- 3. Buat tabel tbl_expedisi (sesuai Supabase asli)
CREATE TABLE IF NOT EXISTS tbl_expedisi (
    datetrans TEXT NOT NULL, -- Transaction date (NOT NULL)
    chanelsales TEXT, -- Channel sales
    orderno TEXT NOT NULL, -- Order number (Primary Key)
    couriername TEXT, -- Courier/Scanner name
    resino TEXT NOT NULL, -- Alternative barcode identifier (NOT NULL)
    created TEXT, -- Created timestamp
    flag TEXT DEFAULT 'NO', -- Flag status
    cekfu INTEGER, -- Check follow up (Boolean as INTEGER)
    CONSTRAINT tbl_expedisi_pkey PRIMARY KEY (orderno)
);

-- 4. Create indices untuk performa query
CREATE INDEX IF NOT EXISTS idx_tbl_resi_created ON tbl_resi(created);
CREATE INDEX IF NOT EXISTS idx_tbl_resi_resi ON tbl_resi(Resi);
CREATE INDEX IF NOT EXISTS idx_tbl_resi_nokarung ON tbl_resi(nokarung);
CREATE INDEX IF NOT EXISTS idx_tbl_resi_schedule ON tbl_resi(schedule);

CREATE INDEX IF NOT EXISTS idx_tbl_expedisi_datetrans ON tbl_expedisi(datetrans);
CREATE INDEX IF NOT EXISTS idx_tbl_expedisi_orderno ON tbl_expedisi(orderno);
CREATE INDEX IF NOT EXISTS idx_tbl_expedisi_resino ON tbl_expedisi(resino);
CREATE INDEX IF NOT EXISTS idx_tbl_expedisi_chanelsales ON tbl_expedisi(chanelsales);
CREATE INDEX IF NOT EXISTS idx_tbl_expedisi_couriername ON tbl_expedisi(couriername);
CREATE INDEX IF NOT EXISTS idx_tbl_expedisi_created ON tbl_expedisi(created);
CREATE INDEX IF NOT EXISTS idx_tbl_expedisi_flag ON tbl_expedisi(flag);

-- 5. Masukkan data sample untuk testing
INSERT OR IGNORE INTO tbl_resi (Resi, Keterangan, nokarung, schedule, created) VALUES
('1234567890123', 'Built-in Scanner Test', 'K001', 'ontime', '2024-01-15T10:30:00Z'),
('9876543210987', 'Camera Scanner Test', 'K002', 'ontime', '2024-01-15T10:35:00Z'),
('5555555555555', 'Built-in Scanner Test', 'K003', 'ontime', '2024-01-15T10:40:00Z');

INSERT OR IGNORE INTO tbl_expedisi (datetrans, chanelsales, orderno, couriername, resino, created, flag, cekfu) VALUES
('2024-01-15T10:30:00Z', 'TOKOPEDIA', 'ORD001', 'Built-in Scanner', '1234567890123', '2024-01-15T10:30:00Z', 'YES', 0),
('2024-01-15T10:35:00Z', 'SHOPEE', 'ORD002', 'Camera Scanner', '9876543210987', '2024-01-15T10:35:00Z', 'YES', 0),
('2024-01-15T10:40:00Z', 'LAZADA', 'ORD003', 'Built-in Scanner', '5555555555555', '2024-01-15T10:40:00Z', 'YES', 0);

-- 6. Verify migration
SELECT 'tbl_resi' as table_name, COUNT(*) as record_count FROM tbl_resi
UNION ALL
SELECT 'tbl_expedisi' as table_name, COUNT(*) as record_count FROM tbl_expedisi;

-- ✅ Migration completed!
-- Database schema sekarang sudah benar dan sesuai dengan Supabase asli

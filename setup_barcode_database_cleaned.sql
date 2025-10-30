-- 🗄️ SETUP DATABASE SUPABASE UNTUK BARCODE SCANNER (CLEANED)
-- Schema yang disesuaikan dengan tabel asli di Supabase
-- Jalankan script ini di Supabase SQL Editor

-- 1. Buat tabel tbl_resi (sesuai dengan Supabase asli)
CREATE TABLE IF NOT EXISTS tbl_resi (
    created TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    Resi TEXT NOT NULL UNIQUE,
    Keterangan TEXT,
    nokarung TEXT,
    schedule TEXT,
    CONSTRAINT tbl_resi_pkey PRIMARY KEY (Resi)
);

-- 2. Buat tabel tbl_expedisi (sesuai dengan Supabase asli)
CREATE TABLE IF NOT EXISTS tbl_expedisi (
    datetrans TIMESTAMP WITH TIME ZONE NOT NULL,
    chanelsales TEXT,
    orderno CHARACTER VARYING NOT NULL CHECK (orderno::text <> ''::text),
    couriername CHARACTER VARYING CHECK (couriername::text <> '#N/A'::text),
    resino CHARACTER VARYING NOT NULL CHECK (resino::text <> ''::text),
    created TIMESTAMP WITHOUT TIME ZONE,
    flag CHARACTER VARYING DEFAULT 'NO'::character varying,
    cekfu BOOLEAN,
    CONSTRAINT tbl_expedisi_pkey PRIMARY KEY (orderno)
);

-- 3. Enable Row Level Security (RLS)
ALTER TABLE tbl_resi ENABLE ROW LEVEL SECURITY;
ALTER TABLE tbl_expedisi ENABLE ROW LEVEL SECURITY;

-- 4. Buat policy untuk read access
CREATE POLICY IF NOT EXISTS "Allow read access tbl_resi" 
ON tbl_resi FOR SELECT USING (true);

CREATE POLICY IF NOT EXISTS "Allow read access tbl_expedisi" 
ON tbl_expedisi FOR SELECT USING (true);

-- 5. Buat policy untuk insert access
CREATE POLICY IF NOT EXISTS "Allow insert access tbl_resi" 
ON tbl_resi FOR INSERT WITH CHECK (true);

CREATE POLICY IF NOT EXISTS "Allow insert access tbl_expedisi" 
ON tbl_expedisi FOR INSERT WITH CHECK (true);

-- 6. Buat policy untuk update access
CREATE POLICY IF NOT EXISTS "Allow update access tbl_resi" 
ON tbl_resi FOR UPDATE USING (true);

CREATE POLICY IF NOT EXISTS "Allow update access tbl_expedisi" 
ON tbl_expedisi FOR UPDATE USING (true);

-- 7. Buat index untuk performa query
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

-- 8. Masukkan data sample untuk testing
INSERT INTO tbl_resi (Resi, Keterangan, nokarung, schedule) VALUES
('1234567890123', 'Built-in Scanner Test', 'K001', 'ontime'),
('9876543210987', 'Camera Scanner Test', 'K002', 'ontime'),
('5555555555555', 'Built-in Scanner Test', 'K003', 'ontime');

INSERT INTO tbl_expedisi (datetrans, chanelsales, orderno, couriername, resino, created, flag, cekfu) VALUES
('2024-01-15 10:30:00+00', 'TOKOPEDIA', 'ORD001', 'Built-in Scanner', '1234567890123', '2024-01-15 10:30:00', 'YES', false),
('2024-01-15 10:35:00+00', 'SHOPEE', 'ORD002', 'Camera Scanner', '9876543210987', '2024-01-15 10:35:00', 'YES', false),
('2024-01-15 10:40:00+00', 'LAZADA', 'ORD003', 'Built-in Scanner', '5555555555555', '2024-01-15 10:40:00', 'YES', false);

-- 9. Verifikasi data
SELECT 
    'tbl_resi' as table_name,
    COUNT(*) as total_records
FROM tbl_resi
UNION ALL
SELECT 
    'tbl_expedisi' as table_name,
    COUNT(*) as total_records
FROM tbl_expedisi;

-- ✅ Database setup selesai!
-- Schema sekarang sesuai dengan tabel asli di Supabase

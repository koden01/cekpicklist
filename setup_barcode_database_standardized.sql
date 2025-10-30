-- 🗄️ SETUP DATABASE SUPABASE UNTUK BARCODE SCANNER (STANDARDIZED)
-- Schema yang disesuaikan dengan Room Database
-- Jalankan script ini di Supabase SQL Editor

-- 1. Buat tabel barcode_scan (sesuai dengan Room: barcode_resi)
CREATE TABLE IF NOT EXISTS barcode_scan (
    id TEXT PRIMARY KEY, -- Sesuai dengan Room (String)
    Resi TEXT NOT NULL, -- Barcode value
    resino TEXT, -- Alternative barcode identifier
    orderno TEXT, -- Order number
    chanelsales TEXT, -- Channel sales
    couriername TEXT, -- Courier/Scanner name
    created TEXT, -- Created timestamp (String format)
    datetrans TEXT, -- Transaction date (String format)
    flag TEXT DEFAULT 'NO', -- Flag status
    cekfu BOOLEAN DEFAULT false, -- Check follow up
    nokarung TEXT, -- Karung number
    schedule TEXT DEFAULT 'ontime', -- Schedule status: 'ontime', 'late', 'batal'
    Keterangan TEXT, -- Description/Scanner type
    -- Additional barcode-specific fields
    scannerType TEXT NOT NULL DEFAULT 'built-in', -- 'built-in' or 'camera'
    scanDuration INTEGER DEFAULT 0, -- Duration in milliseconds
    status TEXT NOT NULL DEFAULT 'success', -- 'success', 'error', 'duplicate'
    deviceInfo TEXT, -- Device information
    locationInfo TEXT, -- Location information if available
    lastUpdated INTEGER DEFAULT 0, -- Last updated timestamp (Long)
    isSynced BOOLEAN DEFAULT true -- Sync status
);

-- 2. Buat tabel barcode_session (sesuai dengan Room: barcode_expedisi)
CREATE TABLE IF NOT EXISTS barcode_session (
    id TEXT PRIMARY KEY, -- Sesuai dengan Room (String)
    resino TEXT, -- Session identifier
    orderno TEXT, -- Order number
    chanelsales TEXT, -- Channel sales
    couriername TEXT, -- Courier/Scanner name
    created TEXT, -- Created timestamp (String format)
    datetrans TEXT, -- Transaction date (String format)
    flag TEXT DEFAULT 'NO', -- Flag status
    cekfu BOOLEAN DEFAULT false, -- Check follow up
    -- Additional session-specific fields
    sessionName TEXT, -- Optional session name
    scannerType TEXT NOT NULL DEFAULT 'built-in',
    totalScans INTEGER DEFAULT 0,
    successfulScans INTEGER DEFAULT 0,
    failedScans INTEGER DEFAULT 0,
    duplicateScans INTEGER DEFAULT 0,
    avgScanTime REAL DEFAULT 0.0, -- Average scan time in milliseconds
    startTime TEXT, -- Start time (String format)
    endTime TEXT, -- End time (String format)
    deviceInfo TEXT, -- Device information
    lastUpdated INTEGER DEFAULT 0, -- Last updated timestamp (Long)
    isSynced BOOLEAN DEFAULT true -- Sync status
);

-- 3. Enable Row Level Security (RLS)
ALTER TABLE barcode_scan ENABLE ROW LEVEL SECURITY;
ALTER TABLE barcode_session ENABLE ROW LEVEL SECURITY;

-- 4. Buat policy untuk read access
CREATE POLICY IF NOT EXISTS "Allow read access barcode_scan" 
ON barcode_scan FOR SELECT USING (true);

CREATE POLICY IF NOT EXISTS "Allow read access barcode_session" 
ON barcode_session FOR SELECT USING (true);

-- 5. Buat policy untuk insert access
CREATE POLICY IF NOT EXISTS "Allow insert access barcode_scan" 
ON barcode_scan FOR INSERT WITH CHECK (true);

CREATE POLICY IF NOT EXISTS "Allow insert access barcode_session" 
ON barcode_session FOR INSERT WITH CHECK (true);

-- 6. Buat policy untuk update access
CREATE POLICY IF NOT EXISTS "Allow update access barcode_scan" 
ON barcode_scan FOR UPDATE USING (true);

CREATE POLICY IF NOT EXISTS "Allow update access barcode_session" 
ON barcode_session FOR UPDATE USING (true);

-- 7. Buat index untuk performa query
CREATE INDEX IF NOT EXISTS idx_barcode_scan_created ON barcode_scan(created);
CREATE INDEX IF NOT EXISTS idx_barcode_scan_resi ON barcode_scan(Resi);
CREATE INDEX IF NOT EXISTS idx_barcode_scan_resino ON barcode_scan(resino);
CREATE INDEX IF NOT EXISTS idx_barcode_scan_scanner_type ON barcode_scan(scannerType);
CREATE INDEX IF NOT EXISTS idx_barcode_scan_status ON barcode_scan(status);
CREATE INDEX IF NOT EXISTS idx_barcode_scan_flag ON barcode_scan(flag);
CREATE INDEX IF NOT EXISTS idx_barcode_scan_schedule ON barcode_scan(schedule);

CREATE INDEX IF NOT EXISTS idx_barcode_session_created ON barcode_session(created);
CREATE INDEX IF NOT EXISTS idx_barcode_session_resino ON barcode_session(resino);
CREATE INDEX IF NOT EXISTS idx_barcode_session_scanner_type ON barcode_session(scannerType);
CREATE INDEX IF NOT EXISTS idx_barcode_session_flag ON barcode_session(flag);

-- 8. Masukkan data sample untuk testing
INSERT INTO barcode_scan (id, Resi, resino, couriername, scannerType, scanDuration, status, schedule, flag, Keterangan, deviceInfo, lastUpdated, isSynced) VALUES
('resi_001', '1234567890123', '1234567890123', 'Built-in Scanner', 'built-in', 150, 'success', 'ontime', 'YES', 'Built-in Scanner Test', 'Device: Test Scanner', 1705123456789, true),
('resi_002', '9876543210987', '9876543210987', 'Built-in Scanner', 'built-in', 120, 'success', 'ontime', 'YES', 'Built-in Scanner Test', 'Device: Test Scanner', 1705123506789, true),
('resi_003', '5555555555555', '5555555555555', 'Camera Scanner', 'camera', 1800, 'success', 'ontime', 'YES', 'Camera Scanner Test', 'Device: Camera Scanner', 1705123556789, true);

-- 9. Verifikasi data
SELECT 
    scannerType,
    COUNT(*) as total_scans,
    COUNT(CASE WHEN status = 'success' THEN 1 END) as successful_scans,
    COUNT(CASE WHEN status = 'error' THEN 1 END) as failed_scans,
    COUNT(CASE WHEN status = 'duplicate' THEN 1 END) as duplicate_scans,
    AVG(scanDuration) as avg_scan_time,
    COUNT(CASE WHEN flag = 'YES' THEN 1 END) as processed_scans,
    COUNT(CASE WHEN schedule = 'ontime' THEN 1 END) as ontime_scans,
    COUNT(CASE WHEN schedule = 'late' THEN 1 END) as late_scans,
    COUNT(CASE WHEN schedule = 'batal' THEN 1 END) as batal_scans
FROM barcode_scan 
GROUP BY scannerType 
ORDER BY scannerType;

-- ✅ Database setup selesai!
-- Sekarang schema Room dan Supabase sudah sama

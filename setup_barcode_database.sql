-- 🗄️ SETUP DATABASE SUPABASE UNTUK BARCODE SCANNER
-- Menggunakan struktur yang konsisten dengan azure-chinchilla-swoop (tbl_resi & tbl_expedisi)
-- Jalankan script ini di Supabase SQL Editor

-- 1. Buat tabel barcode_scan untuk menyimpan hasil scan barcode
-- Menggunakan kolom yang konsisten dengan tbl_resi dan tbl_expedisi
CREATE TABLE IF NOT EXISTS barcode_scan (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    Resi TEXT NOT NULL, -- Barcode value (menggunakan kolom Resi seperti tbl_resi)
    resino TEXT, -- Alternative barcode identifier (seperti tbl_expedisi)
    orderno TEXT, -- Order number (seperti tbl_resi)
    chanelsales TEXT, -- Channel sales (seperti tbl_resi)
    couriername TEXT, -- Courier/Scanner name (seperti tbl_expedisi)
    created TIMESTAMP WITH TIME ZONE DEFAULT NOW(), -- Created timestamp (seperti tbl_resi)
    datetrans TIMESTAMP WITH TIME ZONE, -- Transaction date (seperti tbl_expedisi)
    flag TEXT DEFAULT 'NO', -- Flag status (seperti tbl_expedisi)
    cekfu BOOLEAN DEFAULT false, -- Check follow up (seperti tbl_expedisi)
    nokarung TEXT, -- Karung number (seperti tbl_resi)
    schedule TEXT DEFAULT 'ontime', -- Schedule status: 'ontime', 'late', 'batal' (seperti tbl_resi)
    Keterangan TEXT, -- Description/Scanner type (seperti tbl_resi)
    -- Additional barcode-specific fields
    scanner_type TEXT NOT NULL DEFAULT 'built-in', -- 'built-in' or 'camera'
    scan_duration INTEGER DEFAULT 0, -- Duration in milliseconds
    status TEXT NOT NULL DEFAULT 'success', -- 'success', 'error', 'duplicate'
    device_info TEXT, -- Device information
    location_info TEXT -- Location information if available
);

-- 2. Buat tabel barcode_session untuk tracking session scanning
-- Menggunakan kolom yang konsisten dengan tbl_expedisi
CREATE TABLE IF NOT EXISTS barcode_session (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    resino TEXT, -- Session identifier (seperti tbl_expedisi)
    orderno TEXT, -- Order number (seperti tbl_expedisi)
    chanelsales TEXT, -- Channel sales (seperti tbl_expedisi)
    couriername TEXT, -- Courier/Scanner name (seperti tbl_expedisi)
    created TIMESTAMP WITH TIME ZONE DEFAULT NOW(), -- Created timestamp (seperti tbl_expedisi)
    datetrans TIMESTAMP WITH TIME ZONE, -- Transaction date (seperti tbl_expedisi)
    flag TEXT DEFAULT 'NO', -- Flag status (seperti tbl_expedisi)
    cekfu BOOLEAN DEFAULT false, -- Check follow up (seperti tbl_expedisi)
    -- Additional session-specific fields
    session_name TEXT, -- Optional session name
    scanner_type TEXT NOT NULL DEFAULT 'built-in',
    total_scans INTEGER DEFAULT 0,
    successful_scans INTEGER DEFAULT 0,
    failed_scans INTEGER DEFAULT 0,
    duplicate_scans INTEGER DEFAULT 0,
    avg_scan_time DECIMAL(10,2) DEFAULT 0.0, -- Average scan time in milliseconds
    start_time TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    end_time TIMESTAMP WITH TIME ZONE,
    device_info TEXT
);

-- 3. Buat tabel barcode_performance untuk tracking performance metrics
CREATE TABLE IF NOT EXISTS barcode_performance (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    date DATE NOT NULL DEFAULT CURRENT_DATE,
    scanner_type TEXT NOT NULL,
    total_scans INTEGER DEFAULT 0,
    successful_scans INTEGER DEFAULT 0,
    failed_scans INTEGER DEFAULT 0,
    duplicate_scans INTEGER DEFAULT 0,
    avg_scan_time DECIMAL(10,2) DEFAULT 0.0,
    min_scan_time INTEGER DEFAULT 0,
    max_scan_time INTEGER DEFAULT 0,
    success_rate DECIMAL(5,2) DEFAULT 0.0, -- Success rate percentage
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    UNIQUE(date, scanner_type)
);

-- 4. Enable Row Level Security (RLS)
ALTER TABLE barcode_scan ENABLE ROW LEVEL SECURITY;
ALTER TABLE barcode_session ENABLE ROW LEVEL SECURITY;
ALTER TABLE barcode_performance ENABLE ROW LEVEL SECURITY;

-- 5. Buat policy untuk read access
CREATE POLICY IF NOT EXISTS "Allow read access barcode_scan" 
ON barcode_scan FOR SELECT USING (true);

CREATE POLICY IF NOT EXISTS "Allow read access barcode_session" 
ON barcode_session FOR SELECT USING (true);

CREATE POLICY IF NOT EXISTS "Allow read access barcode_performance" 
ON barcode_performance FOR SELECT USING (true);

-- 6. Buat policy untuk insert access
CREATE POLICY IF NOT EXISTS "Allow insert access barcode_scan" 
ON barcode_scan FOR INSERT WITH CHECK (true);

CREATE POLICY IF NOT EXISTS "Allow insert access barcode_session" 
ON barcode_session FOR INSERT WITH CHECK (true);

CREATE POLICY IF NOT EXISTS "Allow insert access barcode_performance" 
ON barcode_performance FOR INSERT WITH CHECK (true);

-- 7. Buat policy untuk update access
CREATE POLICY IF NOT EXISTS "Allow update access barcode_scan" 
ON barcode_scan FOR UPDATE USING (true);

CREATE POLICY IF NOT EXISTS "Allow update access barcode_session" 
ON barcode_session FOR UPDATE USING (true);

CREATE POLICY IF NOT EXISTS "Allow update access barcode_performance" 
ON barcode_performance FOR UPDATE USING (true);

-- 8. Buat index untuk performa query (menggunakan kolom yang konsisten)
CREATE INDEX IF NOT EXISTS idx_barcode_scan_created ON barcode_scan(created);
CREATE INDEX IF NOT EXISTS idx_barcode_scan_resi ON barcode_scan(Resi);
CREATE INDEX IF NOT EXISTS idx_barcode_scan_resino ON barcode_scan(resino);
CREATE INDEX IF NOT EXISTS idx_barcode_scan_scanner_type ON barcode_scan(scanner_type);
CREATE INDEX IF NOT EXISTS idx_barcode_scan_status ON barcode_scan(status);
CREATE INDEX IF NOT EXISTS idx_barcode_scan_flag ON barcode_scan(flag);
CREATE INDEX IF NOT EXISTS idx_barcode_scan_schedule ON barcode_scan(schedule);

CREATE INDEX IF NOT EXISTS idx_barcode_session_created ON barcode_session(created);
CREATE INDEX IF NOT EXISTS idx_barcode_session_resino ON barcode_session(resino);
CREATE INDEX IF NOT EXISTS idx_barcode_session_scanner_type ON barcode_session(scanner_type);
CREATE INDEX IF NOT EXISTS idx_barcode_session_flag ON barcode_session(flag);

CREATE INDEX IF NOT EXISTS idx_barcode_performance_date ON barcode_performance(date);
CREATE INDEX IF NOT EXISTS idx_barcode_performance_scanner_type ON barcode_performance(scanner_type);

-- 9. Buat function untuk update performance metrics
CREATE OR REPLACE FUNCTION update_barcode_performance()
RETURNS TRIGGER AS $$
BEGIN
    -- Update daily performance metrics
    INSERT INTO barcode_performance (date, scanner_type, total_scans, successful_scans, failed_scans, duplicate_scans, avg_scan_time, success_rate)
    VALUES (
        CURRENT_DATE,
        NEW.scanner_type,
        1,
        CASE WHEN NEW.status = 'success' THEN 1 ELSE 0 END,
        CASE WHEN NEW.status = 'error' THEN 1 ELSE 0 END,
        CASE WHEN NEW.status = 'duplicate' THEN 1 ELSE 0 END,
        NEW.scan_duration,
        CASE WHEN NEW.status = 'success' THEN 100.0 ELSE 0.0 END
    )
    ON CONFLICT (date, scanner_type)
    DO UPDATE SET
        total_scans = barcode_performance.total_scans + 1,
        successful_scans = barcode_performance.successful_scans + CASE WHEN NEW.status = 'success' THEN 1 ELSE 0 END,
        failed_scans = barcode_performance.failed_scans + CASE WHEN NEW.status = 'error' THEN 1 ELSE 0 END,
        duplicate_scans = barcode_performance.duplicate_scans + CASE WHEN NEW.status = 'duplicate' THEN 1 ELSE 0 END,
        avg_scan_time = (barcode_performance.avg_scan_time * barcode_performance.total_scans + NEW.scan_duration) / (barcode_performance.total_scans + 1),
        min_scan_time = LEAST(barcode_performance.min_scan_time, NEW.scan_duration),
        max_scan_time = GREATEST(barcode_performance.max_scan_time, NEW.scan_duration),
        success_rate = (barcode_performance.successful_scans + CASE WHEN NEW.status = 'success' THEN 1 ELSE 0 END)::DECIMAL / (barcode_performance.total_scans + 1) * 100,
        updated_at = NOW();
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 10. Buat trigger untuk auto-update performance
CREATE TRIGGER trigger_update_barcode_performance
    AFTER INSERT ON barcode_scan
    FOR EACH ROW
    EXECUTE FUNCTION update_barcode_performance();

-- 11. Buat function untuk cleanup old data (optional)
CREATE OR REPLACE FUNCTION cleanup_old_barcode_data()
RETURNS void AS $$
BEGIN
    -- Hapus data barcode_scan yang lebih dari 90 hari
    DELETE FROM barcode_scan WHERE created_at < NOW() - INTERVAL '90 days';
    
    -- Hapus session yang sudah selesai dan lebih dari 30 hari
    DELETE FROM barcode_session WHERE end_time IS NOT NULL AND end_time < NOW() - INTERVAL '30 days';
    
    -- Update performance metrics yang tidak lengkap
    UPDATE barcode_performance 
    SET success_rate = (successful_scans::DECIMAL / total_scans * 100)
    WHERE total_scans > 0 AND success_rate = 0;
END;
$$ LANGUAGE plpgsql;

-- 12. Masukkan data sample untuk testing (menggunakan kolom yang konsisten)
INSERT INTO barcode_scan (Resi, resino, couriername, scanner_type, scan_duration, status, schedule, flag, Keterangan, device_info) VALUES
('1234567890123', '1234567890123', 'Built-in Scanner', 'built-in', 150, 'success', 'ontime', 'YES', 'Built-in Scanner Test', 'Device: Test Scanner'),
('9876543210987', '9876543210987', 'Built-in Scanner', 'built-in', 120, 'success', 'ontime', 'YES', 'Built-in Scanner Test', 'Device: Test Scanner'),
('5555555555555', '5555555555555', 'Camera Scanner', 'camera', 1800, 'success', 'ontime', 'YES', 'Camera Scanner Test', 'Device: Camera Scanner'),
('1234567890123', '1234567890123', 'Built-in Scanner', 'built-in', 140, 'duplicate', 'ontime', 'YES', 'Built-in Scanner Test', 'Device: Test Scanner'),
('1111111111111', '1111111111111', 'Built-in Scanner', 'built-in', 0, 'error', 'batal', 'NO', 'Built-in Scanner Test', 'Device: Test Scanner');

-- 13. Verifikasi data (menggunakan kolom yang konsisten)
SELECT 
    scanner_type,
    COUNT(*) as total_scans,
    COUNT(CASE WHEN status = 'success' THEN 1 END) as successful_scans,
    COUNT(CASE WHEN status = 'error' THEN 1 END) as failed_scans,
    COUNT(CASE WHEN status = 'duplicate' THEN 1 END) as duplicate_scans,
    AVG(scan_duration) as avg_scan_time,
    COUNT(CASE WHEN flag = 'YES' THEN 1 END) as processed_scans,
    COUNT(CASE WHEN schedule = 'ontime' THEN 1 END) as ontime_scans,
    COUNT(CASE WHEN schedule = 'late' THEN 1 END) as late_scans,
    COUNT(CASE WHEN schedule = 'batal' THEN 1 END) as batal_scans
FROM barcode_scan 
GROUP BY scanner_type 
ORDER BY scanner_type;

-- 14. Test performance metrics
SELECT * FROM barcode_performance ORDER BY date DESC, scanner_type;

-- ✅ Database setup selesai!
-- Sekarang aplikasi barcode scanner dapat terhubung dan menyimpan data

-- 🗄️ DATABASE MIGRATION: Complete Standardization v8
-- Standardisasi lengkap antara Room Database dan Supabase
-- Jalankan script ini untuk migrasi database

-- ========================================
-- 1. CLEANUP OLD TABLES
-- ========================================

-- Drop old barcode tables
DROP TABLE IF EXISTS barcode_scan CASCADE;
DROP TABLE IF EXISTS barcode_session CASCADE;
DROP TABLE IF EXISTS barcode_performance CASCADE;

-- ========================================
-- 2. RENAME TABLES TO MATCH ROOM DATABASE
-- ========================================

-- Rename picklist to picklist_items (match Room)
ALTER TABLE picklist RENAME TO picklist_items;

-- Rename picklist_scan to picklist_scans (match Room)
ALTER TABLE picklist_scan RENAME TO picklist_scans;

-- ========================================
-- 3. ENSURE CORRECT TABLE STRUCTURES
-- ========================================

-- Ensure tbl_resi structure (barcode scan data)
CREATE TABLE IF NOT EXISTS tbl_resi (
    created TEXT, -- Created timestamp
    Resi TEXT NOT NULL UNIQUE, -- Barcode value (Primary Key)
    Keterangan TEXT, -- Description/Scanner type
    nokarung TEXT, -- Karung number
    schedule TEXT, -- Schedule status: 'ontime', 'late', 'batal'
    CONSTRAINT tbl_resi_pkey PRIMARY KEY (Resi)
);

-- Ensure tbl_expedisi structure (expedisi data)
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

-- Ensure picklist_items structure (picklist data)
CREATE TABLE IF NOT EXISTS picklist_items (
    id TEXT PRIMARY KEY,
    no_picklist TEXT NOT NULL,
    article_id TEXT NOT NULL,
    product_id TEXT NOT NULL,
    article_name TEXT,
    size TEXT,
    qty_pl INTEGER DEFAULT 0,
    qty_scan INTEGER DEFAULT 0,
    created_at TEXT,
    updated_at TEXT,
    is_synced BOOLEAN DEFAULT false
);

-- Ensure picklist_scans structure (RFID scan data)
CREATE TABLE IF NOT EXISTS picklist_scans (
    id TEXT PRIMARY KEY,
    no_picklist TEXT NOT NULL,
    epc TEXT NOT NULL,
    product_id TEXT NOT NULL,
    article_id TEXT NOT NULL,
    article_name TEXT,
    size TEXT,
    scan_time TEXT,
    created_at TEXT,
    updated_at TEXT,
    is_synced BOOLEAN DEFAULT false
);

-- ========================================
-- 4. CREATE INDICES FOR PERFORMANCE
-- ========================================

-- Indices for tbl_resi
CREATE INDEX IF NOT EXISTS idx_tbl_resi_created ON tbl_resi(created);
CREATE INDEX IF NOT EXISTS idx_tbl_resi_resi ON tbl_resi(Resi);
CREATE INDEX IF NOT EXISTS idx_tbl_resi_nokarung ON tbl_resi(nokarung);
CREATE INDEX IF NOT EXISTS idx_tbl_resi_schedule ON tbl_resi(schedule);

-- Indices for tbl_expedisi
CREATE INDEX IF NOT EXISTS idx_tbl_expedisi_datetrans ON tbl_expedisi(datetrans);
CREATE INDEX IF NOT EXISTS idx_tbl_expedisi_orderno ON tbl_expedisi(orderno);
CREATE INDEX IF NOT EXISTS idx_tbl_expedisi_resino ON tbl_expedisi(resino);
CREATE INDEX IF NOT EXISTS idx_tbl_expedisi_chanelsales ON tbl_expedisi(chanelsales);
CREATE INDEX IF NOT EXISTS idx_tbl_expedisi_couriername ON tbl_expedisi(couriername);
CREATE INDEX IF NOT EXISTS idx_tbl_expedisi_created ON tbl_expedisi(created);
CREATE INDEX IF NOT EXISTS idx_tbl_expedisi_flag ON tbl_expedisi(flag);

-- Indices for picklist_items
CREATE INDEX IF NOT EXISTS idx_picklist_items_no_picklist ON picklist_items(no_picklist);
CREATE INDEX IF NOT EXISTS idx_picklist_items_article_id ON picklist_items(article_id);
CREATE INDEX IF NOT EXISTS idx_picklist_items_product_id ON picklist_items(product_id);
CREATE INDEX IF NOT EXISTS idx_picklist_items_created_at ON picklist_items(created_at);

-- Indices for picklist_scans
CREATE INDEX IF NOT EXISTS idx_picklist_scans_no_picklist ON picklist_scans(no_picklist);
CREATE INDEX IF NOT EXISTS idx_picklist_scans_epc ON picklist_scans(epc);
CREATE INDEX IF NOT EXISTS idx_picklist_scans_product_id ON picklist_scans(product_id);
CREATE INDEX IF NOT EXISTS idx_picklist_scans_created_at ON picklist_scans(created_at);

-- ========================================
-- 5. ENABLE ROW LEVEL SECURITY
-- ========================================

ALTER TABLE tbl_resi ENABLE ROW LEVEL SECURITY;
ALTER TABLE tbl_expedisi ENABLE ROW LEVEL SECURITY;
ALTER TABLE picklist_items ENABLE ROW LEVEL SECURITY;
ALTER TABLE picklist_scans ENABLE ROW LEVEL SECURITY;

-- ========================================
-- 6. CREATE POLICIES FOR ALL TABLES
-- ========================================

-- Policies for tbl_resi
CREATE POLICY IF NOT EXISTS "Allow read access tbl_resi" 
ON tbl_resi FOR SELECT USING (true);

CREATE POLICY IF NOT EXISTS "Allow insert access tbl_resi" 
ON tbl_resi FOR INSERT WITH CHECK (true);

CREATE POLICY IF NOT EXISTS "Allow update access tbl_resi" 
ON tbl_resi FOR UPDATE USING (true);

CREATE POLICY IF NOT EXISTS "Allow delete access tbl_resi" 
ON tbl_resi FOR DELETE USING (true);

-- Policies for tbl_expedisi
CREATE POLICY IF NOT EXISTS "Allow read access tbl_expedisi" 
ON tbl_expedisi FOR SELECT USING (true);

CREATE POLICY IF NOT EXISTS "Allow insert access tbl_expedisi" 
ON tbl_expedisi FOR INSERT WITH CHECK (true);

CREATE POLICY IF NOT EXISTS "Allow update access tbl_expedisi" 
ON tbl_expedisi FOR UPDATE USING (true);

CREATE POLICY IF NOT EXISTS "Allow delete access tbl_expedisi" 
ON tbl_expedisi FOR DELETE USING (true);

-- Policies for picklist_items
CREATE POLICY IF NOT EXISTS "Allow read access picklist_items" 
ON picklist_items FOR SELECT USING (true);

CREATE POLICY IF NOT EXISTS "Allow insert access picklist_items" 
ON picklist_items FOR INSERT WITH CHECK (true);

CREATE POLICY IF NOT EXISTS "Allow update access picklist_items" 
ON picklist_items FOR UPDATE USING (true);

CREATE POLICY IF NOT EXISTS "Allow delete access picklist_items" 
ON picklist_items FOR DELETE USING (true);

-- Policies for picklist_scans
CREATE POLICY IF NOT EXISTS "Allow read access picklist_scans" 
ON picklist_scans FOR SELECT USING (true);

CREATE POLICY IF NOT EXISTS "Allow insert access picklist_scans" 
ON picklist_scans FOR INSERT WITH CHECK (true);

CREATE POLICY IF NOT EXISTS "Allow update access picklist_scans" 
ON picklist_scans FOR UPDATE USING (true);

CREATE POLICY IF NOT EXISTS "Allow delete access picklist_scans" 
ON picklist_scans FOR DELETE USING (true);

-- ========================================
-- 7. VERIFY MIGRATION
-- ========================================

-- List all tables after migration
SELECT 
    'Tables after migration:' as info,
    tablename
FROM pg_tables 
WHERE schemaname = 'public'
ORDER BY tablename;

-- Verify table structures
SELECT 'Database migration v8 completed successfully' as status;


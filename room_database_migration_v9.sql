-- 🗄️ ROOM DATABASE MIGRATION: Align with Supabase Structure v9
-- Migration script untuk menyesuaikan Room Database dengan struktur Supabase
-- Jalankan script ini untuk migrasi database lokal

-- ========================================
-- 1. BACKUP DATA LAMA (OPTIONAL)
-- ========================================

-- Backup data lama sebelum migrasi (jika diperlukan)
-- CREATE TABLE picklist_items_backup AS SELECT * FROM picklist_items;
-- CREATE TABLE picklist_scans_backup AS SELECT * FROM picklist_scans;

-- ========================================
-- 2. DROP TABLES LAMA
-- ========================================

-- Drop tabel lama yang tidak sesuai dengan Supabase
DROP TABLE IF EXISTS picklist_items;
DROP TABLE IF EXISTS picklist_scans;

-- ========================================
-- 3. CREATE TABLES BARU SESUAI SUPABASE
-- ========================================

-- Create tabel picklist sesuai dengan Supabase
CREATE TABLE picklist (
    id TEXT NOT NULL PRIMARY KEY, -- UUID dari Supabase
    no_picklist TEXT NOT NULL, -- no_picklist (NOT NULL)
    article_id TEXT NOT NULL, -- article_id (NOT NULL)
    size TEXT, -- size (nullable)
    product_id TEXT, -- product_id (nullable)
    qty INTEGER NOT NULL, -- qty (NOT NULL)
    created_at TEXT, -- created_at timestamp
    article_name TEXT, -- article_name (text)
    notrans TEXT NOT NULL, -- notrans (NOT NULL)
    scan INTEGER NOT NULL DEFAULT 0 -- scan (boolean, DEFAULT false)
    -- HAPUS: lastUpdated dan isSynced - tidak ada di Supabase
);

-- Create tabel picklist_scan sesuai dengan Supabase
CREATE TABLE picklist_scan (
    epc TEXT NOT NULL PRIMARY KEY, -- epc sebagai PRIMARY KEY
    no_picklist TEXT NOT NULL, -- no_picklist (NOT NULL)
    product_id TEXT NOT NULL, -- product_id (NOT NULL)
    article_id TEXT, -- article_id (nullable)
    article_name TEXT, -- article_name (nullable)
    size TEXT, -- size (nullable)
    created_at TEXT, -- created_at timestamp
    notrans TEXT -- notrans (nullable)
    -- HAPUS: isSynced dan lastUpdated - tidak ada di Supabase
);

-- ========================================
-- 4. CREATE INDICES
-- ========================================

-- Indices untuk tabel picklist
CREATE INDEX idx_picklist_no_picklist ON picklist(no_picklist);
CREATE INDEX idx_picklist_article_id ON picklist(article_id);
CREATE INDEX idx_picklist_product_id ON picklist(product_id);
CREATE INDEX idx_picklist_created_at ON picklist(created_at);

-- Indices untuk tabel picklist_scan
CREATE INDEX idx_picklist_scan_no_picklist ON picklist_scan(no_picklist);
CREATE INDEX idx_picklist_scan_epc ON picklist_scan(epc);
CREATE INDEX idx_picklist_scan_product_id ON picklist_scan(product_id);
CREATE INDEX idx_picklist_scan_created_at ON picklist_scan(created_at);

-- ========================================
-- 5. MIGRATE DATA (JIKA ADA)
-- ========================================

-- Migrate data dari backup jika diperlukan
-- INSERT INTO picklist (id, no_picklist, article_id, size, product_id, qty, created_at, article_name, notrans, scan, lastUpdated, isSynced)
-- SELECT id, noPicklist, articleId, size, productId, qtyPl, createdAt, articleName, '', scan, lastUpdated, isSynced
-- FROM picklist_items_backup;

-- INSERT INTO picklist_scan (epc, no_picklist, product_id, article_id, article_name, size, created_at, notrans, isSynced, lastUpdated)
-- SELECT epc, noPicklist, productId, articleId, articleName, size, '', notrans, isSynced, lastUpdated
-- FROM picklist_scans_backup;

-- ========================================
-- 6. VERIFY MIGRATION
-- ========================================

-- Verify tabel baru sudah dibuat
SELECT 'Migration completed successfully' as status;

-- List semua tabel
SELECT name FROM sqlite_master WHERE type='table' AND name LIKE 'picklist%';

-- Verify struktur tabel
PRAGMA table_info(picklist);
PRAGMA table_info(picklist_scan);

-- ========================================
-- 7. CLEANUP (OPTIONAL)
-- ========================================

-- Drop backup tables jika tidak diperlukan
-- DROP TABLE IF EXISTS picklist_items_backup;
-- DROP TABLE IF EXISTS picklist_scans_backup;

SELECT 'Room Database migration to Supabase structure completed' as final_status;

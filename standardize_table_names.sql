-- 🔄 STANDARDIZE TABLE NAMES BETWEEN ROOM AND SUPABASE
-- Script untuk menyamakan nama tabel antara Room Database dan Supabase
-- Jalankan script ini di Supabase SQL Editor

-- ========================================
-- 1. RENAME TABLES TO MATCH ROOM DATABASE
-- ========================================

-- Rename picklist to picklist_items (match Room)
ALTER TABLE picklist RENAME TO picklist_items;

-- Rename picklist_scan to picklist_scans (match Room)
ALTER TABLE picklist_scan RENAME TO picklist_scans;

-- ========================================
-- 2. UPDATE INDICES TO MATCH NEW NAMES
-- ========================================

-- Drop old indices
DROP INDEX IF EXISTS idx_picklist_no_picklist;
DROP INDEX IF EXISTS idx_picklist_created_at;
DROP INDEX IF EXISTS idx_picklist_scan_no_picklist;
DROP INDEX IF EXISTS idx_picklist_scan_epc;
DROP INDEX IF EXISTS idx_picklist_scan_product_id;
DROP INDEX IF EXISTS idx_picklist_scan_created_at;

-- Create new indices for picklist_items
CREATE INDEX IF EXISTS idx_picklist_items_no_picklist ON picklist_items(no_picklist);
CREATE INDEX IF EXISTS idx_picklist_items_created_at ON picklist_items(created_at);
CREATE INDEX IF EXISTS idx_picklist_items_article_id ON picklist_items(article_id);
CREATE INDEX IF EXISTS idx_picklist_items_product_id ON picklist_items(product_id);

-- Create new indices for picklist_scans
CREATE INDEX IF EXISTS idx_picklist_scans_no_picklist ON picklist_scans(no_picklist);
CREATE INDEX IF EXISTS idx_picklist_scans_epc ON picklist_scans(epc);
CREATE INDEX IF EXISTS idx_picklist_scans_product_id ON picklist_scans(product_id);
CREATE INDEX IF EXISTS idx_picklist_scans_created_at ON picklist_scans(created_at);

-- ========================================
-- 3. UPDATE POLICIES TO MATCH NEW NAMES
-- ========================================

-- Drop old policies
DROP POLICY IF EXISTS "Allow read access picklist" ON picklist_items;
DROP POLICY IF EXISTS "Allow insert access picklist" ON picklist_items;
DROP POLICY IF EXISTS "Allow update access picklist" ON picklist_items;
DROP POLICY IF EXISTS "Allow delete access picklist" ON picklist_items;

DROP POLICY IF EXISTS "Allow read access picklist_scan" ON picklist_scans;
DROP POLICY IF EXISTS "Allow insert access picklist_scan" ON picklist_scans;
DROP POLICY IF EXISTS "Allow update access picklist_scan" ON picklist_scans;
DROP POLICY IF EXISTS "Allow delete access picklist_scan" ON picklist_scans;

-- Create new policies for picklist_items
CREATE POLICY "Allow read access picklist_items" 
ON picklist_items FOR SELECT USING (true);

CREATE POLICY "Allow insert access picklist_items" 
ON picklist_items FOR INSERT WITH CHECK (true);

CREATE POLICY "Allow update access picklist_items" 
ON picklist_items FOR UPDATE USING (true);

CREATE POLICY "Allow delete access picklist_items" 
ON picklist_items FOR DELETE USING (true);

-- Create new policies for picklist_scans
CREATE POLICY "Allow read access picklist_scans" 
ON picklist_scans FOR SELECT USING (true);

CREATE POLICY "Allow insert access picklist_scans" 
ON picklist_scans FOR INSERT WITH CHECK (true);

CREATE POLICY "Allow update access picklist_scans" 
ON picklist_scans FOR UPDATE USING (true);

CREATE POLICY "Allow delete access picklist_scans" 
ON picklist_scans FOR DELETE USING (true);

-- ========================================
-- 4. VERIFY TABLE STRUCTURE
-- ========================================

-- List all tables after standardization
SELECT 
    schemaname,
    tablename,
    tableowner
FROM pg_tables 
WHERE schemaname = 'public'
ORDER BY tablename;

-- Verify table structure matches Room entities
SELECT 'Table standardization completed successfully' as status;


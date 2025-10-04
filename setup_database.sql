-- 🗄️ SETUP DATABASE SUPABASE UNTUK CEKPICKLIST APP
-- Jalankan script ini di Supabase SQL Editor

-- 1. Buat tabel picklist
CREATE TABLE IF NOT EXISTS picklist (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    no_picklist TEXT NOT NULL,
    article_id TEXT NOT NULL,
    article_name TEXT NOT NULL,
    size TEXT,
    product_id TEXT,
    qty INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- 2. Buat tabel picklist_scan
CREATE TABLE IF NOT EXISTS picklist_scan (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    no_picklist TEXT NOT NULL,
    article_id TEXT NOT NULL,
    epc TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- 3. Enable Row Level Security (RLS)
ALTER TABLE picklist ENABLE ROW LEVEL SECURITY;
ALTER TABLE picklist_scan ENABLE ROW LEVEL SECURITY;

-- 4. Buat policy untuk read access
CREATE POLICY IF NOT EXISTS "Allow read access picklist" 
ON picklist FOR SELECT USING (true);

CREATE POLICY IF NOT EXISTS "Allow read access picklist_scan" 
ON picklist_scan FOR SELECT USING (true);

-- 5. Buat policy untuk insert access
CREATE POLICY IF NOT EXISTS "Allow insert access picklist_scan" 
ON picklist_scan FOR INSERT WITH CHECK (true);

-- 6. Masukkan data sample untuk testing
INSERT INTO picklist (no_picklist, article_id, article_name, size, qty) VALUES
('PL001', 'ART001', 'T-Shirt', 'M', 10),
('PL001', 'ART001', 'T-Shirt', 'L', 15),
('PL001', 'ART002', 'Jeans', '32', 5),
('PL001', 'ART002', 'Jeans', '34', 8),
('PL002', 'ART003', 'Shoes', '42', 12),
('PL002', 'ART003', 'Shoes', '43', 6),
('PL002', 'ART004', 'Hat', 'One Size', 20),
('PL003', 'ART005', 'Jacket', 'M', 3),
('PL003', 'ART005', 'Jacket', 'L', 4),
('PL003', 'ART005', 'Jacket', 'XL', 2);

-- 7. Verifikasi data
SELECT 
    no_picklist,
    COUNT(*) as total_items,
    SUM(qty) as total_qty
FROM picklist 
GROUP BY no_picklist 
ORDER BY no_picklist;

-- ✅ Database setup selesai!
-- Sekarang aplikasi dapat terhubung dan mengambil data picklist

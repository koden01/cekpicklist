-- Script untuk melihat data di tbl_resi Room Database
-- Jalankan di Android Studio Database Inspector atau SQLite Browser

-- 1. Lihat struktur tabel
.schema tbl_resi

-- 2. Lihat semua data
SELECT * FROM tbl_resi ORDER BY created DESC LIMIT 10;

-- 3. Lihat data dengan format yang lebih readable
SELECT 
    Resi as 'Barcode',
    created as 'Timestamp',
    Keterangan as 'Courier/Scanner',
    nokarung as 'Karung Number',
    schedule as 'Schedule Status'
FROM tbl_resi 
ORDER BY created DESC 
LIMIT 10;

-- 4. Hitung total data
SELECT COUNT(*) as 'Total Records' FROM tbl_resi;

-- 5. Hitung berdasarkan schedule
SELECT 
    schedule as 'Schedule Status',
    COUNT(*) as 'Count'
FROM tbl_resi 
GROUP BY schedule;

-- 6. Hitung berdasarkan nokarung
SELECT 
    nokarung as 'Karung Number',
    COUNT(*) as 'Count'
FROM tbl_resi 
WHERE nokarung IS NOT NULL
GROUP BY nokarung
ORDER BY COUNT(*) DESC;

-- 7. Lihat data terbaru (hari ini)
SELECT 
    Resi,
    created,
    Keterangan,
    nokarung,
    schedule
FROM tbl_resi 
WHERE date(created) = date('now')
ORDER BY created DESC;

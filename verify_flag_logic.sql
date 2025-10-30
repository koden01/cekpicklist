-- Query untuk memverifikasi logika flag di tbl_expedisi
-- Jalankan di Android Studio Database Inspector atau SQLite Browser

-- 1. Lihat semua data di tbl_expedisi dengan flag status
SELECT 
    resino as 'Resi',
    couriername as 'Courier',
    flag as 'Flag Status',
    created as 'Created Date'
FROM tbl_expedisi 
ORDER BY created DESC 
LIMIT 10;

-- 2. Hitung jumlah data berdasarkan flag status
SELECT 
    flag as 'Flag Status',
    COUNT(*) as 'Count'
FROM tbl_expedisi 
GROUP BY flag;

-- 3. Cek apakah ada resi yang ada di tbl_resi tapi flag masih NO
SELECT 
    e.resino as 'Resi di tbl_expedisi',
    e.flag as 'Flag Status',
    r.Resi as 'Resi di tbl_resi',
    r.created as 'Scan Date'
FROM tbl_expedisi e
INNER JOIN tbl_resi r ON e.resino = r.Resi
WHERE e.flag = 'NO'
ORDER BY r.created DESC;

-- 4. Cek apakah ada resi yang ada di tbl_resi tapi flag sudah YES
SELECT 
    e.resino as 'Resi di tbl_expedisi',
    e.flag as 'Flag Status',
    r.Resi as 'Resi di tbl_resi',
    r.created as 'Scan Date'
FROM tbl_expedisi e
INNER JOIN tbl_resi r ON e.resino = r.Resi
WHERE e.flag = 'YES'
ORDER BY r.created DESC;

-- 5. Cek resi yang ada di tbl_expedisi tapi tidak ada di tbl_resi
SELECT 
    e.resino as 'Resi di tbl_expedisi',
    e.flag as 'Flag Status',
    e.couriername as 'Courier'
FROM tbl_expedisi e
LEFT JOIN tbl_resi r ON e.resino = r.Resi
WHERE r.Resi IS NULL
ORDER BY e.created DESC;

-- 6. Update manual flag untuk testing (HATI-HATI!)
-- UPDATE tbl_expedisi SET flag = 'YES' WHERE resino = 'SPXID05475890413A';

-- 7. Reset flag untuk testing (HATI-HATI!)
-- UPDATE tbl_expedisi SET flag = 'NO' WHERE resino = 'SPXID05475890413A';

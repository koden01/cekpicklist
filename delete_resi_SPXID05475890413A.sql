-- Query untuk menghapus resi SPXID05475890413A dari Room Database tbl_resi
-- Jalankan di Android Studio Database Inspector atau SQLite Browser

-- 1. Cek data yang akan dihapus (untuk konfirmasi)
SELECT * FROM tbl_resi WHERE Resi = 'SPXID05475890413A';

-- 2. Hitung berapa banyak data yang akan dihapus
SELECT COUNT(*) as 'Jumlah Data' FROM tbl_resi WHERE Resi = 'SPXID05475890413A';

-- 3. Hapus data resi SPXID05475890413A
DELETE FROM tbl_resi WHERE Resi = 'SPXID05475890413A';

-- 4. Konfirmasi data sudah terhapus
SELECT * FROM tbl_resi WHERE Resi = 'SPXID05475890413A';

-- 5. Lihat total data yang tersisa
SELECT COUNT(*) as 'Total Data Tersisa' FROM tbl_resi;

-- 6. Lihat data terbaru (opsional)
SELECT * FROM tbl_resi ORDER BY created DESC LIMIT 5;

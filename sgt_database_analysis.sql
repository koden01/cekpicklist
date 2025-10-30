-- =============================================
-- SCRIPT ANALISIS DATABASE SGT_GUDANG
-- Database: dbGUNAJAYA
-- Server: 10.10.1.10
-- =============================================

-- 1. LIHAT SEMUA TABEL DI DATABASE dbGUNAJAYA
SELECT 
    TABLE_SCHEMA as 'Schema',
    TABLE_NAME as 'Nama Tabel',
    TABLE_TYPE as 'Tipe',
    CREATE_TIME as 'Tanggal Dibuat'
FROM INFORMATION_SCHEMA.TABLES 
WHERE TABLE_TYPE = 'BASE TABLE'
ORDER BY TABLE_NAME;

-- 2. HITUNG TOTAL TABEL
SELECT 
    COUNT(*) as 'Total Tabel di dbGUNAJAYA'
FROM INFORMATION_SCHEMA.TABLES 
WHERE TABLE_TYPE = 'BASE TABLE';

-- 3. LIHAT TABEL YANG MUNGKIN TERKAIT DENGAN SGT (WAREHOUSE/GUDANG)
SELECT 
    TABLE_NAME as 'Nama Tabel',
    TABLE_SCHEMA as 'Schema'
FROM INFORMATION_SCHEMA.TABLES 
WHERE TABLE_TYPE = 'BASE TABLE'
AND (
    TABLE_NAME LIKE '%SGT%' OR
    TABLE_NAME LIKE '%GUDANG%' OR
    TABLE_NAME LIKE '%WAREHOUSE%' OR
    TABLE_NAME LIKE '%BARANG%' OR
    TABLE_NAME LIKE '%PRODUCT%' OR
    TABLE_NAME LIKE '%ITEM%' OR
    TABLE_NAME LIKE '%STOCK%' OR
    TABLE_NAME LIKE '%INVENTORY%' OR
    TABLE_NAME LIKE '%PICK%' OR
    TABLE_NAME LIKE '%SCAN%' OR
    TABLE_NAME LIKE '%RFID%' OR
    TABLE_NAME LIKE '%MASTER%' OR
    TABLE_NAME LIKE '%TRANS%' OR
    TABLE_NAME LIKE '%DETAIL%'
)
ORDER BY TABLE_NAME;

-- 4. LIHAT SEMUA KOLOM DARI TABEL YANG TERKAIT
SELECT 
    t.TABLE_NAME as 'Nama Tabel',
    c.COLUMN_NAME as 'Nama Kolom',
    c.DATA_TYPE as 'Tipe Data',
    c.CHARACTER_MAXIMUM_LENGTH as 'Panjang Maks',
    c.IS_NULLABLE as 'Boleh Null',
    c.COLUMN_DEFAULT as 'Default Value'
FROM INFORMATION_SCHEMA.TABLES t
INNER JOIN INFORMATION_SCHEMA.COLUMNS c ON t.TABLE_NAME = c.TABLE_NAME
WHERE t.TABLE_TYPE = 'BASE TABLE'
AND (
    t.TABLE_NAME LIKE '%SGT%' OR
    t.TABLE_NAME LIKE '%GUDANG%' OR
    t.TABLE_NAME LIKE '%WAREHOUSE%' OR
    t.TABLE_NAME LIKE '%BARANG%' OR
    t.TABLE_NAME LIKE '%PRODUCT%' OR
    t.TABLE_NAME LIKE '%ITEM%' OR
    t.TABLE_NAME LIKE '%STOCK%' OR
    t.TABLE_NAME LIKE '%INVENTORY%' OR
    t.TABLE_NAME LIKE '%PICK%' OR
    t.TABLE_NAME LIKE '%SCAN%' OR
    t.TABLE_NAME LIKE '%RFID%' OR
    t.TABLE_NAME LIKE '%MASTER%' OR
    t.TABLE_NAME LIKE '%TRANS%' OR
    t.TABLE_NAME LIKE '%DETAIL%'
)
ORDER BY t.TABLE_NAME, c.ORDINAL_POSITION;

-- 5. LIHAT FOREIGN KEY RELATIONS
SELECT 
    fk.name AS 'Foreign Key Name',
    tp.name AS 'Parent Table',
    cp.name AS 'Parent Column',
    tr.name AS 'Referenced Table',
    cr.name AS 'Referenced Column'
FROM sys.foreign_keys AS fk
INNER JOIN sys.tables AS tp ON fk.parent_object_id = tp.object_id
INNER JOIN sys.tables AS tr ON fk.referenced_object_id = tr.object_id
INNER JOIN sys.foreign_key_columns AS fkc ON fkc.constraint_object_id = fk.object_id
INNER JOIN sys.columns AS cp ON fkc.parent_column_id = cp.column_id AND fkc.parent_object_id = cp.object_id
INNER JOIN sys.columns AS cr ON fkc.referenced_column_id = cr.column_id AND fkc.referenced_object_id = cr.object_id
ORDER BY tp.name, fk.name;

-- 6. HITUNG JUMLAH RECORD DI SETIAP TABEL
SELECT 
    t.name AS 'Table Name',
    p.rows AS 'Row Count'
FROM sys.tables t
INNER JOIN sys.partitions p ON t.object_id = p.object_id
WHERE p.index_id IN (0, 1)
ORDER BY p.rows DESC;

-- 7. LIHAT STORED PROCEDURES
SELECT 
    ROUTINE_NAME as 'Nama Stored Procedure',
    ROUTINE_TYPE as 'Tipe',
    CREATED as 'Tanggal Dibuat'
FROM INFORMATION_SCHEMA.ROUTINES
WHERE ROUTINE_TYPE = 'PROCEDURE'
ORDER BY ROUTINE_NAME;

-- 8. LIHAT VIEWS
SELECT 
    TABLE_NAME as 'Nama View',
    VIEW_DEFINITION as 'Definisi View'
FROM INFORMATION_SCHEMA.VIEWS
ORDER BY TABLE_NAME;

-- 9. LIHAT INDEXES
SELECT 
    t.name AS 'Table Name',
    i.name AS 'Index Name',
    i.type_desc AS 'Index Type',
    i.is_unique AS 'Is Unique',
    i.is_primary_key AS 'Is Primary Key'
FROM sys.indexes i
INNER JOIN sys.tables t ON i.object_id = t.object_id
WHERE i.index_id > 0
ORDER BY t.name, i.name;

-- 10. SAMPLE DATA DARI TABEL YANG TERKAIT (ganti nama_tabel dengan nama tabel yang ada)
-- SELECT TOP 5 * FROM nama_tabel;

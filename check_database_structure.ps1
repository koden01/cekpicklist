# Script untuk cek struktur database
Write-Host "🔍 Checking database structure..."

# Cek apakah database ada
Write-Host "📊 Available tables:"
adb shell "run-as com.example.cekpicklist.debug sqlite3 databases/cekpicklist_local.db '.tables'"

# Cek struktur picklist_scan
Write-Host "`n📋 Structure of picklist_scan table:"
adb shell "run-as com.example.cekpicklist.debug sqlite3 databases/cekpicklist_local.db '.schema picklist_scan'"

# Cek struktur picklist
Write-Host "`n📋 Structure of picklist table:"
adb shell "run-as com.example.cekpicklist.debug sqlite3 databases/cekpicklist_local.db '.schema picklist'"

# Cek data di picklist_scan
Write-Host "`n📊 Data in picklist_scan table:"
adb shell "run-as com.example.cekpicklist.debug sqlite3 databases/cekpicklist_local.db 'SELECT COUNT(*) as count FROM picklist_scan;'"

# Cek data di picklist
Write-Host "`n📊 Data in picklist table:"
adb shell "run-as com.example.cekpicklist.debug sqlite3 databases/cekpicklist_local.db 'SELECT COUNT(*) as count FROM picklist;'"

Write-Host "`n✅ Database structure check completed."

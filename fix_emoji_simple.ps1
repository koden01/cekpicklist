# Fix emoji encoding in README.md
$content = Get-Content "README.md" -Raw -Encoding UTF8

# Replace problematic emojis with simple text
$content = $content -replace "ðŸ†", "🏆"
$content = $content -replace "ðŸ—ï¸", "🏗️"
$content = $content -replace "ðŸ"±", "📱"
$content = $content -replace "ðŸ§ ", "🧠"
$content = $content -replace "ðŸ"Š", "📊"
$content = $content -replace "ðŸŒ", "🌐"
$content = $content -replace "ðŸ—„ï¸", "🗄️"
$content = $content -replace "ðŸ"„", "📋"
$content = $content -replace "ðŸ'¾", "💾"
$content = $content -replace "ðŸ› ï¸", "🔧"
$content = $content -replace "ðŸš€", "🚀"
$content = $content -replace "ðŸ"§", "⚙️"
$content = $content -replace "ðŸ"ˆ", "📈"
$content = $content -replace "ðŸŽ¯", "🎯"
$content = $content -replace "ðŸŽ¨", "🎨"
$content = $content -replace "ðŸŽ‰", "🎉"

# Write back to file
Set-Content "README.md" -Value $content -Encoding UTF8

Write-Host "✅ Emoji encoding fixed in README.md" -ForegroundColor Green

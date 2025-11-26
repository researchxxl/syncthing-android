while ($true) {
    $stringToDelete = Read-Host "Please enter string to remove containing rows in xml files"
    $basePath = ($PSScriptRoot + "\..\app\src\main\res")
    #
    Get-ChildItem -Path $basePath -Recurse -Filter strings.xml | ForEach-Object {
        $file = $_.FullName
        $lines = Get-Content $file -Raw
        $lineCount = ($lines -split "`r?`n").Count
        $filteredLines = [regex]::Replace($lines, ("^\s*<string[^>]*\bname\s*=\s*""[^""]*" + [regex]::Escape($stringToDelete) + "[^""]*""[^>]*>.*?</string>\r?\n?"), "", "Singleline, Multiline")
        $filteredLines = $filteredLines -split "`r?`n"
        $filteredLines = $filteredLines | Where-Object { $_ -notmatch [regex]::Escape($stringToDelete) }
        if ($lineCount -ne $filteredLines.Count) {
            Set-Content -Path $file -Value ($filteredLines -join "`r`n") -NoNewline
            Write-Host "Processed: $file"
        }
    }
}
#
Exit 0

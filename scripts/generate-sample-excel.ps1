$base = "D:\Project\agent-audit\data\samples"

function New-TestXlsx {
    param(
        [string]$filePath,
        [array]$table
    )

    $tmp = Join-Path $env:TEMP ("xlsx_" + [guid]::NewGuid().ToString())
    New-Item -ItemType Directory -Path $tmp | Out-Null
    New-Item -ItemType Directory -Path (Join-Path $tmp "_rels") | Out-Null
    New-Item -ItemType Directory -Path (Join-Path $tmp "xl") | Out-Null
    New-Item -ItemType Directory -Path (Join-Path $tmp "xl\_rels") | Out-Null
    New-Item -ItemType Directory -Path (Join-Path $tmp "xl\worksheets") | Out-Null

    $contentTypes = @'
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
</Types>
'@

    $rels = @'
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>
'@

    $workbook = @'
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="Sheet1" sheetId="1" r:id="rId1"/>
  </sheets>
</workbook>
'@

    $workbookRels = @'
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
</Relationships>
'@

    $rowsXml = ""
    for ($r = 0; $r -lt $table.Count; $r++) {
        $cellsXml = ""
        for ($c = 0; $c -lt $table[$r].Count; $c++) {
            $col = [char](65 + $c)
            $addr = "$col$($r + 1)"
            $val = [System.Security.SecurityElement]::Escape([string]$table[$r][$c])
            $cellsXml += "<c r='$addr' t='inlineStr'><is><t>$val</t></is></c>"
        }
        $rowsXml += "<row r='$($r + 1)'>$cellsXml</row>"
    }

    $sheet = "<?xml version='1.0' encoding='UTF-8' standalone='yes'?><worksheet xmlns='http://schemas.openxmlformats.org/spreadsheetml/2006/main'><sheetData>$rowsXml</sheetData></worksheet>"

    $utf8 = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText((Join-Path $tmp "[Content_Types].xml"), $contentTypes, $utf8)
    [System.IO.File]::WriteAllText((Join-Path $tmp "_rels\\.rels"), $rels, $utf8)
    [System.IO.File]::WriteAllText((Join-Path $tmp "xl\\workbook.xml"), $workbook, $utf8)
    [System.IO.File]::WriteAllText((Join-Path $tmp "xl\\_rels\\workbook.xml.rels"), $workbookRels, $utf8)
    [System.IO.File]::WriteAllText((Join-Path $tmp "xl\\worksheets\\sheet1.xml"), $sheet, $utf8)

    $zip = "$filePath.zip"
    if (Test-Path $zip) { Remove-Item $zip -Force }
    if (Test-Path $filePath) { Remove-Item $filePath -Force }

    Compress-Archive -Path (Join-Path $tmp "*") -DestinationPath $zip -CompressionLevel Optimal
    Move-Item -Path $zip -Destination $filePath -Force

    Remove-Item -Path $tmp -Recurse -Force
}

New-TestXlsx -filePath (Join-Path $base "audit_datasource_demo.xlsx") -table @(
  @("source_name", "type", "department", "rate", "updated_at"),
  @("finance_system", "MYSQL", "finance", "88", "2026-03-01"),
  @("purchase_sheet", "XLSX", "purchase", "75", "2026-03-02"),
  @("rd_issue_db", "POSTGRESQL", "rd", "91", "2026-03-03")
)

New-TestXlsx -filePath (Join-Path $base "audit_issue_import.xlsx") -table @(
  @("issue_id", "issue_type", "owner_dept", "status", "due_date"),
  @("ISS-1001", "process_violation", "finance", "processing", "2026-03-20"),
  @("ISS-1002", "over_permission", "purchase", "pending", "2026-03-22"),
  @("ISS-1003", "missing_log", "rd", "done", "2026-03-18")
)

Get-ChildItem $base | Select-Object Name, Length, LastWriteTime

package com.audit.data.service.infrastructure;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
/**
 * 文件行读取器：支持 CSV/XLSX/JSON/TXT 并统一输出 JSON 行文本。
 */
public class FileRowReader {

    private final ObjectMapper objectMapper;

    public FileRowReader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<String> readRows(String filePath, String fileName) {
        if (isBlank(filePath)) {
            throw new IllegalArgumentException("鏂囦欢鏁版嵁婧愮己灏戞枃浠惰矾寰");
        }
        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("鏂囦欢涓嶅瓨鍦? " + filePath);
        }

        String lower = nvl(fileName).toLowerCase();
        if (lower.endsWith(".csv") || lower.endsWith(".txt")) {
            return readTextStructuredRows(path, lower.endsWith(".txt") ? '|' : ',');
        }
        if (lower.endsWith(".json")) {
            return readJsonRows(path);
        }
        if (lower.endsWith(".xlsx") || lower.endsWith(".xls")) {
            return readExcelRows(path);
        }
        throw new IllegalArgumentException("涓嶆敮鎸佺殑鏂囦欢绫诲瀷");
    }

    private List<String> readExcelRows(Path path) {
        try (InputStream inputStream = Files.newInputStream(path); Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
            if (sheet == null) {
                return List.of();
            }

            int firstRow = sheet.getFirstRowNum();
            Row headerRow = sheet.getRow(firstRow);
            if (headerRow == null) {
                return List.of();
            }

            int lastCell = Math.max(headerRow.getLastCellNum(), (short) 0);
            if (lastCell == 0) {
                return List.of();
            }

            DataFormatter formatter = new DataFormatter();
            List<String> headers = new ArrayList<>();
            for (int i = 0; i < lastCell; i++) {
                Cell cell = headerRow.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                String key = cell == null ? "" : formatter.formatCellValue(cell).trim();
                if (key.isEmpty()) {
                    key = "col_" + (i + 1);
                }
                headers.add(key);
            }

            List<String> rows = new ArrayList<>();
            int lastRow = sheet.getLastRowNum();
            for (int r = firstRow + 1; r <= lastRow && rows.size() < 10000; r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                Map<String, Object> item = new HashMap<>();
                boolean hasValue = false;
                for (int c = 0; c < headers.size(); c++) {
                    Cell cell = row.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    String value = cell == null ? "" : formatter.formatCellValue(cell).trim();
                    if (!value.isEmpty()) {
                        hasValue = true;
                    }
                    item.put(headers.get(c), value);
                }
                if (hasValue) {
                    rows.add(toJson(item));
                }
            }
            return rows;
        } catch (Exception ex) {
            throw new IllegalArgumentException("璇诲彇 Excel 澶辫触: " + ex.getMessage());
        }
    }

    private List<String> readTextStructuredRows(Path path, char delimiter) {
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String headerLine = reader.readLine();
            if (headerLine == null) return List.of();
            String[] headers = splitLine(headerLine, delimiter);
            List<String> rows = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null && rows.size() < 10000) {
                String[] values = splitLine(line, delimiter);
                Map<String, Object> row = new HashMap<>();
                for (int i = 0; i < headers.length; i++) {
                    String key = headers[i].trim();
                    if (key.isEmpty()) key = "col_" + (i + 1);
                    row.put(key, i < values.length ? values[i].trim() : "");
                }
                rows.add(toJson(row));
            }
            return rows;
        } catch (IOException ex) {
            throw new IllegalArgumentException("璇诲彇鏂囦欢澶辫触: " + ex.getMessage());
        }
    }

    private List<String> readJsonRows(Path path) {
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8).trim();
            if (content.startsWith("[")) {
                List<Map<String, Object>> list = objectMapper.readValue(content, new TypeReference<>() {});
                List<String> rows = new ArrayList<>();
                for (Map<String, Object> item : list) {
                    rows.add(toJson(item));
                }
                return rows;
            }

            List<String> rows = new ArrayList<>();
            try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null && rows.size() < 10000) {
                    String trim = line.trim();
                    if (!trim.isEmpty()) rows.add(trim);
                }
            }
            return rows;
        } catch (Exception ex) {
            throw new IllegalArgumentException("瑙ｆ瀽 JSON 鏂囦欢澶辫触: " + ex.getMessage());
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalArgumentException("JSON搴忓垪鍖栧け璐");
        }
    }

    private String[] splitLine(String line, char delimiter) {
        return line.split(Pattern.quote(String.valueOf(delimiter)), -1);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String nvl(String value) {
        return value == null ? "" : value;
    }
}


package ru.eltex.producthub.importer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

/** Лист Excel как таблица: заголовки ищутся по тексту, строки читаются как есть. Исходный файл не изменяется. */
final class ExcelTable {

    private final Map<String, Integer> columns = new HashMap<>();
    private final List<Map<Integer, Object>> rows = new ArrayList<>();
    private final List<Integer> rowNumbers = new ArrayList<>();

    private ExcelTable() {
    }

    /**
     * @param sheetName имя листа или null для первого листа
     * @param markers   заголовки, по которым определяется строка заголовков (в первых 5 строках)
     */
    static ExcelTable read(Path file, String sheetName, String... markers) throws IOException {
        try (InputStream in = Files.newInputStream(file); Workbook wb = WorkbookFactory.create(in)) {
            Sheet sheet = sheetName == null ? wb.getSheetAt(0) : wb.getSheet(sheetName);
            if (sheet == null) {
                throw new IOException("В файле " + file.getFileName() + " нет листа \"" + sheetName + "\"");
            }
            DataFormatter formatter = new DataFormatter();
            ExcelTable table = new ExcelTable();
            int headerRow = -1;
            for (int i = 0; i <= Math.min(4, sheet.getLastRowNum()) && headerRow < 0; i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                List<String> texts = new ArrayList<>();
                row.forEach(c -> texts.add(formatter.formatCellValue(c).trim()));
                boolean all = true;
                for (String marker : markers) {
                    all &= texts.contains(marker);
                }
                if (all) {
                    headerRow = i;
                    for (int c = 0; c < texts.size(); c++) {
                        table.columns.putIfAbsent(texts.get(c), c);
                    }
                }
            }
            if (headerRow < 0) {
                throw new IOException("В файле " + file.getFileName() + " не найдена строка заголовков: "
                        + String.join(", ", markers));
            }
            for (int i = headerRow + 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                Map<Integer, Object> values = new HashMap<>();
                for (Cell cell : row) {
                    Object v = value(cell, formatter);
                    if (v != null) {
                        values.put(cell.getColumnIndex(), v);
                    }
                }
                table.rows.add(values);
                table.rowNumbers.add(i + 1);
            }
            return table;
        }
    }

    private static Object value(Cell cell, DataFormatter formatter) {
        CellType type = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
        switch (type) {
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getLocalDateTimeCellValue().toLocalDate();
                }
                String s = formatter.formatCellValue(cell).trim();
                return s.isEmpty() ? null : s;
            case STRING:
                String t = cell.getStringCellValue().trim();
                return t.isEmpty() ? null : t;
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            default:
                return null;
        }
    }

    int size() {
        return rows.size();
    }

    int rowNumber(int index) {
        return rowNumbers.get(index);
    }

    boolean hasColumn(String header) {
        return columns.containsKey(header);
    }

    /** Текст ячейки. Пустая ячейка и "-" дают null (SPEC.md 5.11). */
    String text(int index, String header) {
        Object v = raw(index, header);
        if (v == null) {
            return null;
        }
        String s = v instanceof LocalDate d ? d.toString() : v.toString().trim();
        return s.isEmpty() || s.equals("-") ? null : s;
    }

    LocalDate date(int index, String header) {
        Object v = raw(index, header);
        if (v instanceof LocalDate d) {
            return d;
        }
        if (v instanceof String s) {
            try {
                return LocalDate.parse(s.trim());
            } catch (java.time.format.DateTimeParseException e) {
                return null;
            }
        }
        return null;
    }

    private Object raw(int index, String header) {
        Integer col = columns.get(header);
        return col == null ? null : rows.get(index).get(col);
    }
}

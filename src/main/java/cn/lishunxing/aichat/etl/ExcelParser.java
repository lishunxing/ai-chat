package cn.lishunxing.aichat.etl;

import cn.lishunxing.aichat.source.BlogDocument;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Excel 解析器 —— 使用 Apache POI 解析 .xlsx，将工作表转换为 Markdown 表格
 *
 * @author lishunxing
 */
public class ExcelParser implements DocumentParser {

    private static final Logger log = LoggerFactory.getLogger(ExcelParser.class);

    private static final int MAX_ROWS = 10000;

    @Override
    public List<String> supportedExtensions() {
        return List.of(".xlsx");
    }

    @Override
    public List<ParsedSection> parse(BlogDocument document) {
        byte[] data = document.getBinaryContent();
        if (data == null || data.length == 0) {
            log.warn("Empty Excel content: {}", document.getPath());
            return List.of();
        }

        String sourcePath = document.getPath();
        String title = extractFileName(sourcePath);
        List<ParsedSection> sections = new ArrayList<>();

        try (ByteArrayInputStream bis = new ByteArrayInputStream(data);
             Workbook workbook = new XSSFWorkbook(bis)) {

            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();

            for (int sheetIdx = 0; sheetIdx < workbook.getNumberOfSheets(); sheetIdx++) {
                Sheet sheet = workbook.getSheetAt(sheetIdx);

                if (sheet.getPhysicalNumberOfRows() == 0) {
                    continue;
                }

                String tableText = sheetToMarkdownTable(sheet, evaluator);
                if (!tableText.isEmpty()) {
                    sections.add(new ParsedSection(sheet.getSheetName(), tableText, sourcePath, title));
                }
            }
        } catch (IOException e) {
            log.error("Failed to parse Excel file: {}", sourcePath, e);
            throw new RuntimeException("Excel 文件解析失败: " + sourcePath, e);
        }

        return sections;
    }

    private String sheetToMarkdownTable(Sheet sheet, FormulaEvaluator evaluator) {
        StringBuilder sb = new StringBuilder();
        int lastRowNum = Math.min(sheet.getLastRowNum(), MAX_ROWS - 1);

        Row headerRow = sheet.getRow(sheet.getFirstRowNum());
        if (headerRow == null) return "";

        int colCount = headerRow.getLastCellNum();
        if (colCount <= 0) return "";

        // Header row
        StringBuilder headerLine = new StringBuilder("|");
        StringBuilder separatorLine = new StringBuilder("|");
        for (int col = 0; col < colCount; col++) {
            String headerText = getCellStringValue(headerRow, col, evaluator);
            headerLine.append(" ").append(headerText.isEmpty() ? "列" + (col + 1) : headerText).append(" |");
            separatorLine.append(" --- |");
        }
        sb.append(headerLine).append("\n");
        sb.append(separatorLine).append("\n");

        // Data rows
        for (int rowIdx = sheet.getFirstRowNum() + 1; rowIdx <= lastRowNum; rowIdx++) {
            Row row = sheet.getRow(rowIdx);
            if (row == null) continue;

            boolean hasData = false;
            StringBuilder rowLine = new StringBuilder("|");
            for (int col = 0; col < colCount; col++) {
                String cellValue = getCellStringValue(row, col, evaluator);
                if (!cellValue.isEmpty()) hasData = true;
                rowLine.append(" ").append(cellValue).append(" |");
            }
            if (hasData) {
                sb.append(rowLine).append("\n");
            }
        }

        return sb.toString().trim();
    }

    private String getCellStringValue(Row row, int colIdx, FormulaEvaluator evaluator) {
        Cell cell = row.getCell(colIdx, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return "";

        if (cell.getCellType() == CellType.FORMULA) {
            try {
                CellValue evaluated = evaluator.evaluate(cell);
                return formatCellValue(evaluated);
            } catch (Exception e) {
                return cell.getCellFormula();
            }
        }

        return formatCellValue(cell);
    }

    private String formatCellValue(CellValue cellValue) {
        return switch (cellValue.getCellType()) {
            case NUMERIC -> {
                double val = cellValue.getNumberValue();
                yield val == (long) val ? String.valueOf((long) val) : String.valueOf(val);
            }
            case STRING -> cellValue.getStringValue();
            case BOOLEAN -> String.valueOf(cellValue.getBooleanValue());
            default -> "";
        };
    }

    private String formatCellValue(Cell cell) {
        return switch (cell.getCellType()) {
            case NUMERIC -> {
                double val = cell.getNumericCellValue();
                yield val == (long) val ? String.valueOf((long) val) : String.valueOf(val);
            }
            case STRING -> cell.getStringCellValue().trim();
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> "";
        };
    }

    private String extractFileName(String path) {
        String name = path.substring(path.lastIndexOf('/') + 1);
        return name.replace(".xlsx", "");
    }
}

package cn.lishunxing.aichat.etl;

import cn.lishunxing.aichat.source.BlogDocument;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * PDF 解析器 —— 使用 Apache PDFBox 按页提取文本
 *
 * @author lishunxing
 */
public class PdfParser implements DocumentParser {

    private static final Logger log = LoggerFactory.getLogger(PdfParser.class);

    @Override
    public List<String> supportedExtensions() {
        return List.of(".pdf");
    }

    @Override
    public List<ParsedSection> parse(BlogDocument document) {
        byte[] data = document.getBinaryContent();
        if (data == null || data.length == 0) {
            log.warn("Empty PDF content: {}", document.getPath());
            return List.of();
        }

        String sourcePath = document.getPath();
        String title = extractFileName(sourcePath);
        List<ParsedSection> sections = new ArrayList<>();

        try (PDDocument pdf = Loader.loadPDF(data)) {
            PDDocumentInformation info = pdf.getDocumentInformation();
            if (info.getTitle() != null && !info.getTitle().isBlank()) {
                title = info.getTitle();
            }

            PDFTextStripper stripper = new PDFTextStripper();
            int pageCount = pdf.getNumberOfPages();

            for (int page = 1; page <= pageCount; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String pageText = stripper.getText(pdf);

                if (pageText != null && !pageText.isBlank()) {
                    sections.add(new ParsedSection(
                            "第" + page + "页",
                            pageText.trim(),
                            sourcePath,
                            title
                    ));
                }
            }
        } catch (IOException e) {
            log.error("Failed to parse PDF: {}", sourcePath, e);
            throw new RuntimeException("PDF 解析失败: " + sourcePath, e);
        }

        return sections;
    }

    private String extractFileName(String path) {
        String name = path.substring(path.lastIndexOf('/') + 1);
        return name.replace(".pdf", "");
    }
}

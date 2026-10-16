package com.ekp.processing.extract;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

/**
 * Extracts text from PDFs with Apache PDFBox. Sorting by position keeps the
 * reading order sane for multi-column layouts. Scanned/image-only PDFs yield
 * little or no text — OCR would be a separate, heavier pipeline.
 */
@Component
public class PdfTextExtractor implements TextExtractor {

    @Override
    public String contentType() {
        return "application/pdf";
    }

    @Override
    public String extract(byte[] bytes) throws Exception {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        }
    }
}

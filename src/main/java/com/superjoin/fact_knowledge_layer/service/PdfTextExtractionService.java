package com.superjoin.fact_knowledge_layer.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class PdfTextExtractionService {

    public record ExtractedPdf(String text, int pageCount) { }

    /**
     * Extracts text page by page and inserts explicit page markers
     * ("[page 3]") into the text stream. This gives the LLM (and, later,
     * humans reading evidence quotes) a cheap way to anchor a fact to a
     * page without us needing per-fact bounding boxes or OCR coordinates.
     */
    public ExtractedPdf extract(byte[] pdfBytes) {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            int pages = document.getNumberOfPages();
            StringBuilder sb = new StringBuilder();
            PDFTextStripper stripper = new PDFTextStripper();
            for (int i = 1; i <= pages; i++) {
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                String pageText = stripper.getText(document);
                sb.append("\n[page ").append(i).append("]\n").append(pageText);
            }
            String text = sb.toString().trim();
            if (text.isBlank()) {
                throw new IllegalStateException(
                        "No extractable text found (the PDF may be a scanned image without OCR).");
            }
            return new ExtractedPdf(text, pages);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read PDF: " + e.getMessage(), e);
        }
    }
}

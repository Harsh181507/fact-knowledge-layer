package com.superjoin.fact_knowledge_layer.service;

import com.superjoin.fact_knowledge_layer.model.Fact;
import com.superjoin.fact_knowledge_layer.model.SourceDocument;
import com.superjoin.fact_knowledge_layer.repository.DocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Service
public class DocumentProcessingService {

    private static final Logger log = LoggerFactory.getLogger(DocumentProcessingService.class);

    private final PdfTextExtractionService pdfTextExtractionService;
    private final FactExtractionService factExtractionService;
    private final FactComparisonService factComparisonService;
    private final DocumentRepository documentRepository;

    public DocumentProcessingService(PdfTextExtractionService pdfTextExtractionService,
                                      FactExtractionService factExtractionService,
                                      FactComparisonService factComparisonService,
                                      DocumentRepository documentRepository) {
        this.pdfTextExtractionService = pdfTextExtractionService;
        this.factExtractionService = factExtractionService;
        this.factComparisonService = factComparisonService;
        this.documentRepository = documentRepository;
    }

    /**
     * Processes a new PDF end to end. Runs synchronously so the HTTP response
     * can return the extracted facts directly - fine for the prototype scale
     * described in the assignment; a production version would move this to a
     * background job and let the client poll GET /api/documents/{id}.
     */
    public SourceDocument processUpload(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty");
        }
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "document.pdf";

        SourceDocument document = new SourceDocument();
        document.setFilename(filename);
        document.setStatus(SourceDocument.ProcessingStatus.PROCESSING);
        document = documentRepository.save(document);

        try {
            byte[] bytes = file.getBytes();
            PdfTextExtractionService.ExtractedPdf extracted = pdfTextExtractionService.extract(bytes);
            document.setRawText(extracted.text());
            document.setPageCount(extracted.pageCount());
            document = documentRepository.save(document);

            List<Fact> facts = factExtractionService.extractAndPersist(document);
            log.info("Extracted {} fact(s) from '{}'", facts.size(), filename);

            factComparisonService.compareAgainstExisting(facts);

            document.setStatus(SourceDocument.ProcessingStatus.COMPLETED);
            return documentRepository.save(document);
        } catch (IOException e) {
            return markFailed(document, "Could not read uploaded file: " + e.getMessage());
        } catch (Exception e) {
            log.error("Processing failed for '{}'", filename, e);
            return markFailed(document, e.getMessage());
        }
    }

    private SourceDocument markFailed(SourceDocument document, String note) {
        document.setStatus(SourceDocument.ProcessingStatus.FAILED);
        document.setProcessingNote(note);
        return documentRepository.save(document);
    }
}

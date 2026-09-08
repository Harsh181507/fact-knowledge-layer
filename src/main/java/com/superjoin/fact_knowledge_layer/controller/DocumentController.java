package com.superjoin.fact_knowledge_layer.controller;

import com.superjoin.fact_knowledge_layer.dto.DocumentResponseDTO;
import com.superjoin.fact_knowledge_layer.dto.FactResponseDTO;
import com.superjoin.fact_knowledge_layer.model.Fact;
import com.superjoin.fact_knowledge_layer.model.SourceDocument;
import com.superjoin.fact_knowledge_layer.repository.DocumentRepository;
import com.superjoin.fact_knowledge_layer.repository.FactRepository;
import com.superjoin.fact_knowledge_layer.service.DocumentProcessingService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentProcessingService processingService;
    private final DocumentRepository documentRepository;
    private final FactRepository factRepository;

    public DocumentController(DocumentProcessingService processingService,
                               DocumentRepository documentRepository,
                               FactRepository factRepository) {
        this.processingService = processingService;
        this.documentRepository = documentRepository;
        this.factRepository = factRepository;
    }

    /** Upload a PDF. Extracts facts, links evidence, and compares them against
     *  every previously uploaded document, all before returning. */
    @PostMapping
    public DocumentResponseDTO upload(@RequestParam("file") MultipartFile file) {
        SourceDocument doc = processingService.processUpload(file);
        return toResponse(doc);
    }

    @GetMapping
    public List<DocumentResponseDTO> list() {
        return documentRepository.findAll().stream().map(this::toResponse).toList();
    }

    @GetMapping("/{id}")
    public DocumentResponseDTO get(@PathVariable Long id) {
        SourceDocument doc = documentRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Document " + id + " not found"));
        return toResponse(doc);
    }

    private DocumentResponseDTO toResponse(SourceDocument doc) {
        List<Fact> facts = factRepository.findByDocument_Id(doc.getId());
        List<FactResponseDTO> factDtos = facts.stream().map(FactResponseDTO::from).toList();
        return DocumentResponseDTO.from(doc, factDtos);
    }
}

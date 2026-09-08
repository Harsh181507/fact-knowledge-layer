package com.superjoin.fact_knowledge_layer.dto;

import com.superjoin.fact_knowledge_layer.model.SourceDocument;

import java.time.Instant;
import java.util.List;

public class DocumentResponseDTO {
    public Long id;
    public String filename;
    public int pageCount;
    public Instant uploadedAt;
    public String status;
    public String processingNote;
    public List<FactResponseDTO> facts;

    public static DocumentResponseDTO from(SourceDocument doc, List<FactResponseDTO> facts) {
        DocumentResponseDTO dto = new DocumentResponseDTO();
        dto.id = doc.getId();
        dto.filename = doc.getFilename();
        dto.pageCount = doc.getPageCount();
        dto.uploadedAt = doc.getUploadedAt();
        dto.status = doc.getStatus().name();
        dto.processingNote = doc.getProcessingNote();
        dto.facts = facts;
        return dto;
    }
}

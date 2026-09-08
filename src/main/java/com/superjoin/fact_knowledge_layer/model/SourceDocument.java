package com.superjoin.fact_knowledge_layer.model;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "source_document")
public class SourceDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String filename;

    @Lob
    @Column(name = "raw_text")
    private String rawText;

    @Column(name = "page_count")
    private int pageCount;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt = Instant.now();

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ProcessingStatus status = ProcessingStatus.PROCESSING;

    /** Populated only if extraction failed outright (e.g. unreadable PDF). */
    @Column(name = "processing_note", length = 2000)
    private String processingNote;

    public enum ProcessingStatus { PROCESSING, COMPLETED, FAILED }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }

    public String getRawText() { return rawText; }
    public void setRawText(String rawText) { this.rawText = rawText; }

    public int getPageCount() { return pageCount; }
    public void setPageCount(int pageCount) { this.pageCount = pageCount; }

    public Instant getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(Instant uploadedAt) { this.uploadedAt = uploadedAt; }

    public ProcessingStatus getStatus() { return status; }
    public void setStatus(ProcessingStatus status) { this.status = status; }

    public String getProcessingNote() { return processingNote; }
    public void setProcessingNote(String processingNote) { this.processingNote = processingNote; }
}

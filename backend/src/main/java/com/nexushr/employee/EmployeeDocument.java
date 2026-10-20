package com.nexushr.employee;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "employee_documents")
public class EmployeeDocument {
  @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
  @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "employee_id", nullable = false) private Employee employee;
  @Column(name = "document_type", nullable = false, length = 40) private String documentType;
  @Column(name = "file_name", nullable = false, length = 255) private String fileName;
  @Column(name = "content_type", nullable = false, length = 120) private String contentType;
  @Column(name = "size_bytes", nullable = false) private long sizeBytes;
  @Column(name = "uploaded_at", nullable = false, updatable = false) private Instant uploadedAt = Instant.now();

  protected EmployeeDocument() {}

  public EmployeeDocument(Employee employee, EmployeeDtos.DocumentRequest request) {
    this.employee = employee;
    documentType = request.documentType().trim().toUpperCase();
    fileName = request.fileName().trim();
    contentType = request.contentType().trim();
    sizeBytes = request.sizeBytes();
  }

  public UUID getId() { return id; }
  public String getDocumentType() { return documentType; }
  public String getFileName() { return fileName; }
  public String getContentType() { return contentType; }
  public long getSizeBytes() { return sizeBytes; }
  public Instant getUploadedAt() { return uploadedAt; }
}

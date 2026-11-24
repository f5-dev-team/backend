package com.f5.scholarship_backend.domain.document.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.f5.scholarship_backend.domain.document.entity.VersionDocument;

public interface VersionDocumentRepository extends JpaRepository<VersionDocument, VersionDocument.Key> { }

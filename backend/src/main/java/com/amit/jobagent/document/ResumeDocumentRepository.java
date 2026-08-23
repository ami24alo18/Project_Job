package com.amit.jobagent.document;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
interface ResumeDocumentRepository extends JpaRepository<ResumeDocument,UUID>{List<ResumeDocument>findByProfileIdOrderByCreatedAtDescIdAsc(UUID profileId);Optional<ResumeDocument>findByProfileIdAndSha256ChecksumAndStatusNot(UUID profileId,String checksum,DocumentStatus status);List<ResumeDocument>findByProfileIdAndActiveTrue(UUID profileId);Optional<ResumeDocument>findFirstByProfileIdAndActiveTrueOrderByCreatedAtDesc(UUID profileId);}

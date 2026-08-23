package com.amit.jobagent.document;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/resume-documents")
@SecurityRequirement(name = "basicAuth")
public class ResumeDocumentController {
    private final ResumeDocumentService service;

    public ResumeDocumentController(ResumeDocumentService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResumeDocumentResponse upload(@RequestPart("file") MultipartFile file) {
        return service.upload(file);
    }

    @GetMapping
    public List<ResumeDocumentResponse> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public ResumeDocumentResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @GetMapping("/{id}/extracted-text")
    public ExtractedTextResponse text(@PathVariable UUID id) {
        return service.extractedText(id);
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<ByteArrayResource> download(@PathVariable UUID id) {
        var d = service.download(id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(d.contentType())).header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(d.fileName(), StandardCharsets.UTF_8).build().toString()).contentLength(d.content().length).body(new ByteArrayResource(d.content()));
    }

    @PostMapping("/{id}/activate")
    public ResumeDocumentResponse activate(@PathVariable UUID id) {
        return service.activate(id);
    }

    @PostMapping("/{id}/archive")
    public ResumeDocumentResponse archive(@PathVariable UUID id) {
        return service.archive(id);
    }
}

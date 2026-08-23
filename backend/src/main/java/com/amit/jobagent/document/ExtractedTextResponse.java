package com.amit.jobagent.document;
import java.util.UUID;
public record ExtractedTextResponse(UUID documentId,DocumentStatus status,String extractedText,String extractionError){}

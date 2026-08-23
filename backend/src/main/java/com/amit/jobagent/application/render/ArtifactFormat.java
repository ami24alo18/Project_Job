package com.amit.jobagent.application.render;

import com.amit.jobagent.application.ArtifactType;

enum ArtifactFormat {
    HTML(ArtifactType.HTML_PREVIEW, "html", "text/html;charset=UTF-8"),
    PDF(ArtifactType.PDF_RESUME, "pdf", "application/pdf"),
    DOCX(
            ArtifactType.DOCX_RESUME,
            "docx",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    final ArtifactType type;
    final String extension;
    final String contentType;

    ArtifactFormat(ArtifactType type, String extension, String contentType) {
        this.type = type;
        this.extension = extension;
        this.contentType = contentType;
    }

    static ArtifactFormat forType(ArtifactType type) {
        return switch (type) {
            case HTML_PREVIEW -> HTML;
            case PDF_RESUME -> PDF;
            case DOCX_RESUME -> DOCX;
        };
    }
}

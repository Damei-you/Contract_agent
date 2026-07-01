package com.yy.agent.contractmvp.service.document;

import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;

/**
 * 统一的文件正文抽取入口。当前使用 Apache Tika 自动识别 PDF、Office 与纯文本等常见格式。
 */
@Service
public class TikaDocumentTextExtractor {

    private static final int MAX_EXTRACTED_CHARS = 1_000_000;

    private final Tika tika = new Tika();

    /**
     * 从上传文件中抽取正文文本。抽取失败或文本为空时返回明确的 HTTP 错误，供上传接口直接透传。
     */
    public ExtractedDocument extract(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Uploaded file is empty.");
        }

        String filename = StringUtils.cleanPath(file.getOriginalFilename() == null ? "" : file.getOriginalFilename());
        String declaredContentType = file.getContentType() == null ? "" : file.getContentType();
        String detectedContentType = detectContentType(file, filename);

        Metadata metadata = new Metadata();
        if (!filename.isBlank()) {
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, filename);
        }

        String text;
        try (InputStream input = file.getInputStream()) {
            text = normalizeText(tika.parseToString(input, metadata, MAX_EXTRACTED_CHARS));
        } catch (IOException | TikaException e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Failed to parse uploaded file: " + e.getMessage(), e);
        } catch (LinkageError e) {
            // PDFBox scans the system font directory (C:\Windows\Fonts on Windows)
            // during static initialization. A corrupt .ttf/.ttc font file causes
            // ExceptionInInitializerError (first request) or NoClassDefFoundError
            // (subsequent requests). Both extend LinkageError.
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to initialize the PDF font subsystem. A system font file " +
                    "may be corrupt — please check C:\\Windows\\Fonts for damaged " +
                    ".ttf/.ttc files and remove or reinstall them. Cause: " + e.getMessage(), e);
        }

        if (detectedContentType.isBlank()) {
            detectedContentType = metadata.get(HttpHeaders.CONTENT_TYPE) == null ? "" : metadata.get(HttpHeaders.CONTENT_TYPE);
        }
        if (text.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "No readable text was extracted from the uploaded file.");
        }

        return new ExtractedDocument(filename, declaredContentType, detectedContentType, file.getSize(), text);
    }

    private String detectContentType(MultipartFile file, String filename) {
        try (InputStream input = file.getInputStream()) {
            return tika.detect(input, filename);
        } catch (IOException e) {
            return "";
        }
    }

    private static String normalizeText(String raw) {
        if (raw == null) {
            return "";
        }
        return raw
                .replace('\u00A0', ' ')
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[\\t\\x0B\\f]+", " ")
                .replaceAll(" *\\n *", "\n")
                .replaceAll("\\n{4,}", "\n\n\n")
                .trim();
    }
}

package com.projektzitd.file;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.security.Principal;
import java.util.List;
import java.util.Map;

/**
 * File management endpoints — all require a fully authenticated session.
 */
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    // ── List files (own + shared) ─────────────────────────────────────────────

    @GetMapping
    public List<FileDto> list(Principal principal) {
        return fileService.listAccessible(principal.getName())
                .stream()
                .map(FileDto::from)
                .toList();
    }

    // ── Upload ────────────────────────────────────────────────────────────────

    @PostMapping("/upload")
    public ResponseEntity<FileDto> upload(@RequestParam("file") MultipartFile file,
                                          Principal principal) throws IOException {
        FileEntity saved = fileService.upload(principal.getName(), file);
        return ResponseEntity.status(HttpStatus.CREATED).body(FileDto.from(saved));
    }

    // ── Download ──────────────────────────────────────────────────────────────

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id, Principal principal) {
        Path filePath = fileService.resolveForDownload(principal.getName(), id);
        FileEntity meta = fileService.getMeta(id);
        Resource resource = new PathResource(filePath);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + meta.getOriginalName() + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    // ── Share ─────────────────────────────────────────────────────────────────

    @PostMapping("/{id}/share")
    public ResponseEntity<String> share(@PathVariable Long id,
                                        @RequestBody Map<String, String> body,
                                        Principal principal) {
        String targetUser = body.get("username");
        if (targetUser == null || targetUser.isBlank()) {
            return ResponseEntity.badRequest().body("username required");
        }
        fileService.share(principal.getName(), id, targetUser);
        return ResponseEntity.ok("File shared with " + targetUser);
    }

    // ── DTO ───────────────────────────────────────────────────────────────────

    record FileDto(Long id, String originalName, String owner, String uploadedAt, String sharedWith) {
        static FileDto from(FileEntity e) {
            return new FileDto(
                    e.getId(),
                    e.getOriginalName(),
                    e.getOwner().getUsername(),
                    e.getUploadedAt().toString(),
                    e.getSharedWith());
        }
    }
}

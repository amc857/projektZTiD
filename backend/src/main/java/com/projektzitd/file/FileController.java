package com.projektzitd.file;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.Principal;
import java.util.List;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

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
                        ContentDisposition.attachment()
                                .filename(meta.getOriginalName(), StandardCharsets.UTF_8)
                                .build()
                                .toString())
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

    // ── Delete (Owner or ADMIN) ───────────────────────────────────────────────

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, Principal principal) throws IOException {
        fileService.delete(principal.getName(), id);
        return ResponseEntity.noContent().build();
    }

    // ── Admin-only: wszystkie pliki w systemie (przykład @PreAuthorize) ───────

    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    public List<FileDto> listAll() {
        return fileService.listAll() // np. fileRepository.findAll()
                .stream()
                .map(FileDto::from)
                .toList();
    }

    @GetMapping("/admin/user/{username}")
    @PreAuthorize("hasRole('ADMIN')")
    public List<FileDto> getFilesByUsername(@PathVariable String username) {
        // Wcześniej listAccessible(): dla użytkownika-admina zwracało WSZYSTKIE pliki w systemie,
        // a dla zwykłego także cudze pliki mu udostępnione.
        return fileService.listOwnedBy(username).stream()
                .map(FileDto::from)
                .toList();
    }

    @PatchMapping("/{id}/rename")
    public ResponseEntity<?> renameFile(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String newName = body.get("originalName");
        if (newName == null || newName.isBlank()) {
            return ResponseEntity.badRequest().body("Nazwa pliku nie może być pusta");
        }
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        fileService.renameFile(id, newName.trim(), auth.getName(), isAdmin);
        return ResponseEntity.ok().build();
    }
}

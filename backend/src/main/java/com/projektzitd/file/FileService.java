package com.projektzitd.file;

import com.projektzitd.user.User;
import com.projektzitd.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FileService {

    @Value("${app.upload.dir}")
    private String uploadDir;

    private final FileRepository fileRepository;
    private final UserRepository userRepository;

    // ── Upload ────────────────────────────────────────────────────────────────

    @Transactional
    public FileEntity upload(String ownerUsername, MultipartFile multipartFile) throws IOException {
        User owner = getUser(ownerUsername);

        // Store with random UUID name to prevent path traversal / filename collisions
        String storageName = UUID.randomUUID() + "_" + sanitize(multipartFile.getOriginalFilename());
        Path dest = Paths.get(uploadDir).resolve(storageName).toAbsolutePath().normalize();

        // Ensure destination is inside upload directory (path traversal guard)
        if (!dest.startsWith(Paths.get(uploadDir).toAbsolutePath())) {
            throw new SecurityException("Invalid file path");
        }

        Files.createDirectories(dest.getParent());
        multipartFile.transferTo(dest);

        FileEntity entity = new FileEntity();
        entity.setOwner(owner);
        entity.setOriginalName(multipartFile.getOriginalFilename());
        entity.setStoragePath(storageName);
        return fileRepository.save(entity);
    }

    // ── List ──────────────────────────────────────────────────────────────────

    public List<FileEntity> listAccessible(String username) {
        User user = getUser(username);
        if (isAdmin(user)) {
            return fileRepository.findAll(); // Admin widzi wszystkie pliki
        }
        return fileRepository.findAccessibleByUser(user, username);
    }

    public List<FileEntity> listAll() {
        return fileRepository.findAll();
    }

    /** Pliki, których właścicielem jest dany użytkownik (panel admina). */
    public List<FileEntity> listOwnedBy(String username) {
        return fileRepository.findByOwner(getUser(username));
    }

    // ── Download ──────────────────────────────────────────────────────────────

    public Path resolveForDownload(String username, Long fileId) {
        User user = getUser(username);
        FileEntity file = fileRepository.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("File not found"));

        // Admin może pobrać każdy plik; zwykły user tylko swój lub udostępniony
        if (!isAdmin(user)
                && !file.getOwner().getUsername().equals(username)
                && !isSharedWith(file, username)) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied");
        }

        return Paths.get(uploadDir).resolve(file.getStoragePath()).toAbsolutePath().normalize();
    }

    public FileEntity getMeta(Long fileId) {
        return fileRepository.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("File not found"));
    }

    // ── Share ─────────────────────────────────────────────────────────────────

    @Transactional
    public void share(String ownerUsername, Long fileId, String targetUsername) {
        FileEntity file = fileRepository.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("File not found"));

        if (!file.getOwner().getUsername().equals(ownerUsername)) {
            throw new org.springframework.security.access.AccessDeniedException("Only the owner can share a file");
        }

        // Ensure target user exists
        getUser(targetUsername);

        if (!isSharedWith(file, targetUsername)) {
            String updated = file.getSharedWith().isBlank()
                    ? targetUsername
                    : file.getSharedWith() + "," + targetUsername;
            file.setSharedWith(updated);
            fileRepository.save(file);
        }
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @Transactional
    public void delete(String username, Long fileId) throws IOException {
        User user = getUser(username);
        FileEntity file = fileRepository.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("File not found"));

        // Tylko właściciel LUB Admin może usunąć plik
        if (!isAdmin(user) && !file.getOwner().getUsername().equals(username)) {
            throw new org.springframework.security.access.AccessDeniedException("Only owner or ADMIN can delete this file");
        }

        Path filePath = Paths.get(uploadDir).resolve(file.getStoragePath()).toAbsolutePath().normalize();
        Files.deleteIfExists(filePath);
        fileRepository.delete(file);
    }


    @Transactional
    public void renameFile(Long id, String newName, String requester, boolean isAdmin) {
        FileEntity file = fileRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Nie znaleziono pliku"));

        // Porównujemy nazwę właściciela (wcześniej porównywano obiekt User ze Stringiem -> zawsze false)
        if (!isAdmin && !file.getOwner().getUsername().equals(requester)) {
            throw new org.springframework.security.access.AccessDeniedException("Brak uprawnień do zmiany nazwy pliku");
        }

        file.setOriginalName(newName);
        fileRepository.save(file);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean isSharedWith(FileEntity file, String username) {
        return Arrays.asList(file.getSharedWith().split(",")).contains(username);
    }

    private User getUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }

    /** Strip path separators from filename to prevent path traversal. */
    private String sanitize(String filename) {
        if (filename == null) return "upload";
        return filename.replaceAll("[/\\\\:*?\"<>|]", "_");
    }

    private boolean isAdmin(User user) {
        return "ROLE_ADMIN".equals(user.getRole());
    }

}

package com.projektzitd.file;

import com.projektzitd.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Represents a file uploaded by a user.
 * Files are stored on disk; only metadata lives in DB.
 */
@Entity
@Table(name = "files")
@Getter @Setter @NoArgsConstructor
public class FileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    /** Original filename as uploaded by the user. */
    @Column(nullable = false)
    private String originalName;

    /** Path on disk relative to upload directory. */
    @Column(nullable = false)
    private String storagePath;

    @Column(nullable = false)
    private Instant uploadedAt = Instant.now();

    /**
     * Comma-separated list of usernames this file is shared with.
     * Simple approach — sufficient for demo scope.
     */
    @Column(length = 2000)
    private String sharedWith = "";
}

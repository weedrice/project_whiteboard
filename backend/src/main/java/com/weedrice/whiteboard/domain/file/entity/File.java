package com.weedrice.whiteboard.domain.file.entity;

import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.common.entity.BaseTimeEntity;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "files", indexes = {
        @Index(name = "idx_files_uploader", columnList = "uploader_id"),
        @Index(name = "idx_files_related", columnList = "related_type, related_id")
})
public class File extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "file_id")
    private Long fileId;

    @Column(name = "file_path", length = 255, nullable = false)
    private String filePath;

    @Column(name = "original_name", length = 255, nullable = false)
    private String originalName;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "mime_type", length = 100, nullable = false)
    private String mimeType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploader_id", nullable = false)
    private User uploader;

    @Column(name = "related_id")
    private Long relatedId;

    @Column(name = "related_type", length = 50)
    private String relatedType; // POST, COMMENT 등

    @Builder
    public File(String filePath, String originalName, Long fileSize, String mimeType, User uploader, Long relatedId, String relatedType) {
        this.filePath = filePath;
        this.originalName = originalName;
        this.fileSize = fileSize;
        this.mimeType = mimeType;
        this.uploader = uploader;
        this.relatedId = relatedId;
        this.relatedType = relatedType;
    }

    public void updateRelatedInfo(Long relatedId, String relatedType) {
        this.relatedId = relatedId;
        this.relatedType = relatedType;
    }
}

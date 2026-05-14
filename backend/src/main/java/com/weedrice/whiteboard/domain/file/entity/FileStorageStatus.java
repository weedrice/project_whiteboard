package com.weedrice.whiteboard.domain.file.entity;

public enum FileStorageStatus {
    PENDING_UPLOAD,
    ACTIVE,
    PENDING_DELETE,
    DELETING,
    DELETE_FAILED
}

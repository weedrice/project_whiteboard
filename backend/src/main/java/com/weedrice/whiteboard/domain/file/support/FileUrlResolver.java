package com.weedrice.whiteboard.domain.file.support;

public final class FileUrlResolver {

    private FileUrlResolver() {
    }

    public static String resolve(Long fileId) {
        return "/api/v1/files/" + fileId;
    }
}

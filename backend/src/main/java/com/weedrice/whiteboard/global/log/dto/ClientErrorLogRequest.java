package com.weedrice.whiteboard.global.log.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ClientErrorLogRequest {

    public enum Source {
        VUE,
        WINDOW_ERROR,
        UNHANDLED_REJECTION
    }

    @NotNull
    private Source source;

    @NotBlank
    @Size(max = 100)
    private String errorType;

    @NotBlank
    @Size(max = 500)
    private String message;

    @Size(max = 2500)
    private String stackTrace;

    @Size(max = 100)
    private String component;

    @Size(max = 500)
    private String info;

    @NotBlank
    @Size(max = 500)
    private String pageUrl;

    @NotBlank
    @Size(max = 64)
    private String commitHash;
}

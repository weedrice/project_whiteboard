package com.weedrice.whiteboard.global.log.dto;

import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Getter
@Setter
public class ErrorLogSearchRequest {

    private String errorType;

    private String errorCode;

    private Integer httpStatus;

    private String isResolved;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;

    private String requestUri;
}

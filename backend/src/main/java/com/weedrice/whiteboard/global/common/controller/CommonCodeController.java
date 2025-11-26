package com.weedrice.whiteboard.global.common.controller;

import com.weedrice.whiteboard.global.common.ApiResponse;
import com.weedrice.whiteboard.global.common.entity.CommonCodeDetail;
import com.weedrice.whiteboard.global.common.service.CommonCodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/codes")
public class CommonCodeController {

    private final CommonCodeService commonCodeService;

    @GetMapping("/{typeCode}")
    public ApiResponse<List<CodeResponse>> getCodes(@PathVariable String typeCode) {
        List<CommonCodeDetail> codes = commonCodeService.getCommonCodes(typeCode);
        List<CodeResponse> response = codes.stream()
                .map(CodeResponse::new)
                .collect(Collectors.toList());
        return ApiResponse.success(response);
    }

    @lombok.Getter
    private static class CodeResponse {
        private final String code;
        private final String name;
        private final int sortOrder;

        public CodeResponse(CommonCodeDetail detail) {
            this.code = detail.getCodeValue();
            this.name = detail.getCodeName();
            this.sortOrder = detail.getSortOrder();
        }
    }
}

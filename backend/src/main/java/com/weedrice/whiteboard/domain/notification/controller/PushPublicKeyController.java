package com.weedrice.whiteboard.domain.notification.controller;

import com.weedrice.whiteboard.domain.notification.dto.PushPublicKeyResponse;
import com.weedrice.whiteboard.domain.notification.service.PushPublicKeyService;
import com.weedrice.whiteboard.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/push")
@RequiredArgsConstructor
public class PushPublicKeyController {

    private final PushPublicKeyService pushPublicKeyService;

    @GetMapping("/public-key")
    public ApiResponse<PushPublicKeyResponse> getPublicKey() {
        return ApiResponse.success(pushPublicKeyService.getPublicKey());
    }
}

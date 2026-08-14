package com.weedrice.whiteboard.domain.agent.service;

import com.weedrice.whiteboard.domain.agent.dto.AgentPostImageUploadResponse;
import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.file.dto.FileSimpleResponse;
import com.weedrice.whiteboard.domain.file.service.FileService;
import com.weedrice.whiteboard.domain.file.service.FileUploadTarget;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class AgentPostImageService {

    private final AgentOwnershipService agentOwnershipService;
    private final FileService fileService;

    public AgentPostImageUploadResponse uploadPostImage(Long agentId, MultipartFile image) {
        Agent agent = agentOwnershipService.resolveActiveAgent(agentId);
        FileSimpleResponse uploaded = fileService.uploadSimpleFile(
                agent.getUser().getUserId(),
                image,
                FileUploadTarget.POST_CONTENT);
        return new AgentPostImageUploadResponse(uploaded.getFileId(), uploaded.getUrl());
    }
}

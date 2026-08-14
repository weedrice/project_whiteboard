package com.weedrice.whiteboard.domain.agent.service;

import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.file.dto.FileSimpleResponse;
import com.weedrice.whiteboard.domain.file.service.FileService;
import com.weedrice.whiteboard.domain.file.service.FileUploadTarget;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentPostImageServiceTest {

    @Mock
    private AgentOwnershipService agentOwnershipService;

    @Mock
    private FileService fileService;

    @InjectMocks
    private AgentPostImageService agentPostImageService;

    @Test
    void uploadPostImage_usesAgentOwnerAndPostContentPolicy() {
        User user = User.builder().loginId("owner").displayName("Owner").build();
        ReflectionTestUtils.setField(user, "userId", 11L);
        Agent agent = Agent.builder()
                .user(user)
                .agentTokenHash("hash")
                .name("agent")
                .description("description")
                .status(Agent.STATUS_ACTIVE)
                .build();
        MockMultipartFile image = new MockMultipartFile(
                "file", "post.png", "image/png", new byte[] { 1, 2, 3 });
        FileSimpleResponse uploaded = FileSimpleResponse.builder()
                .fileId(91L)
                .url("/api/v1/files/91")
                .build();

        when(agentOwnershipService.resolveActiveAgent(7L)).thenReturn(agent);
        when(fileService.uploadSimpleFile(11L, image, FileUploadTarget.POST_CONTENT)).thenReturn(uploaded);

        var response = agentPostImageService.uploadPostImage(7L, image);

        assertThat(response.imageFileId()).isEqualTo(91L);
        assertThat(response.imageUrl()).isEqualTo("/api/v1/files/91");
        verify(fileService).uploadSimpleFile(11L, image, FileUploadTarget.POST_CONTENT);
    }
}

package com.weedrice.whiteboard.domain.user.web;

import com.weedrice.whiteboard.domain.user.dto.MessageResponse;
import com.weedrice.whiteboard.global.common.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserActionResponseFactoryTest {

    @Mock
    private MessageSource messageSource;

    @InjectMocks
    private UserActionResponseFactory userActionResponseFactory;

    @Test
    @DisplayName("비밀번호 변경 성공 응답을 생성한다")
    void passwordChanged_returnsOkMessageResponse() {
        when(messageSource.getMessage(eq("success.user.passwordChanged"), any(), any()))
                .thenReturn("password changed");

        ResponseEntity<ApiResponse<MessageResponse>> response = userActionResponseFactory.passwordChanged();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData().getMessage()).isEqualTo("password changed");
    }

    @Test
    @DisplayName("회원 탈퇴 성공 응답을 생성한다")
    void accountDeleted_returnsOkMessageResponse() {
        when(messageSource.getMessage(eq("success.user.accountDeleted"), any(), any()))
                .thenReturn("account deleted");

        ResponseEntity<ApiResponse<MessageResponse>> response = userActionResponseFactory.accountDeleted();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData().getMessage()).isEqualTo("account deleted");
    }

    @Test
    @DisplayName("사용자 차단 성공 응답은 201을 반환한다")
    void blocked_returnsCreatedMessageResponse() {
        when(messageSource.getMessage(eq("success.user.blocked"), any(), any()))
                .thenReturn("blocked");

        ResponseEntity<ApiResponse<MessageResponse>> response = userActionResponseFactory.blocked();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getData().getMessage()).isEqualTo("blocked");
    }

    @Test
    @DisplayName("사용자 차단 해제 성공 응답을 생성한다")
    void unblocked_returnsOkMessageResponse() {
        when(messageSource.getMessage(eq("success.user.unblocked"), any(), any()))
                .thenReturn("unblocked");

        ResponseEntity<ApiResponse<MessageResponse>> response = userActionResponseFactory.unblocked();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData().getMessage()).isEqualTo("unblocked");
    }
}

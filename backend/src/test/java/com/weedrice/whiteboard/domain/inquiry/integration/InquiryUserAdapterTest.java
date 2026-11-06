package com.weedrice.whiteboard.domain.inquiry.integration;

import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InquiryUserAdapterTest {
    @Mock
    private UserRepository userRepository;
    @InjectMocks
    private InquiryUserAdapter adapter;

    @Test
    void publicNamesMaskWithdrawalWithoutChangingInternalIdentityAndRestoreOnReactivation() {
        User requester = User.builder().loginId("requester").displayName("Requester").build();
        User staff = User.builder().loginId("staff").displayName("Original staff name").build();
        ReflectionTestUtils.setField(requester, "userId", 10L);
        ReflectionTestUtils.setField(staff, "userId", 99L);
        staff.delete(LocalDateTime.of(2026, 11, 6, 9, 0));
        List<Long> ids = List.of(10L, 99L);
        when(userRepository.findAllById(ids)).thenReturn(List.of(requester, staff));

        assertThat(adapter.findPublicDisplayNames(ids))
                .containsEntry(10L, "Requester")
                .containsEntry(99L, "탈퇴한 사용자");
        assertThat(adapter.findDisplayNames(ids)).containsEntry(99L, "Original staff name");
        assertThat(staff.getDisplayName()).isEqualTo("Original staff name");
        assertThat(staff.getLoginId()).isEqualTo("staff");

        staff.activate();

        assertThat(adapter.findPublicDisplayNames(ids)).containsEntry(99L, "Original staff name");
    }
}

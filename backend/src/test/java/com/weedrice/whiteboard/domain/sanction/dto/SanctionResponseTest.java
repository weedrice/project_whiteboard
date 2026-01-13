package com.weedrice.whiteboard.domain.sanction.dto;

import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.sanction.entity.Sanction;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class SanctionResponseTest {

    @Test
    @DisplayName("SanctionResponse.from() 테스트")
    void from() {
        User user = User.builder().displayName("User").build();
        ReflectionTestUtils.setField(user, "userId", 100L);

        Admin admin = Admin.builder().build();
        ReflectionTestUtils.setField(admin, "adminId", 200L);

        Sanction sanction = Sanction.builder().build();
        ReflectionTestUtils.setField(sanction, "sanctionId", 1L);
        ReflectionTestUtils.setField(sanction, "targetUser", user);
        ReflectionTestUtils.setField(sanction, "admin", admin);
        ReflectionTestUtils.setField(sanction, "type", "BAN");
        ReflectionTestUtils.setField(sanction, "remark", "Reason");
        ReflectionTestUtils.setField(sanction, "startDate", LocalDateTime.now());
        ReflectionTestUtils.setField(sanction, "endDate", LocalDateTime.now().plusDays(1));
        ReflectionTestUtils.setField(sanction, "contentId", 50L);
        ReflectionTestUtils.setField(sanction, "contentType", "POST");

        SanctionResponse response = SanctionResponse.from(sanction);

        assertThat(response.getSanctionId()).isEqualTo(1L);
        assertThat(response.getTargetUserId()).isEqualTo(100L);
        assertThat(response.getTargetUserDisplayName()).isEqualTo("User");
        assertThat(response.getAdminId()).isEqualTo(200L);
        assertThat(response.getType()).isEqualTo("BAN");
        assertThat(response.getRemark()).isEqualTo("Reason");
        assertThat(response.getContentId()).isEqualTo(50L);
        assertThat(response.getContentType()).isEqualTo("POST");
    }
}

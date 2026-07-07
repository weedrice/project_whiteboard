package com.weedrice.whiteboard.domain.user.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class DisplayNameHistoryTest {

    @Test
    @DisplayName("DisplayNameHistory 생성 성공")
    void createDisplayNameHistory_success() {
        // given
        User user = User.builder().build();
        String previousName = "OldName";
        String newName = "NewName";
        LocalDateTime changedAt = LocalDateTime.of(2026, 7, 7, 12, 0);

        // when
        DisplayNameHistory history = DisplayNameHistory.builder()
                .user(user)
                .previousName(previousName)
                .newName(newName)
                .changedAt(changedAt)
                .build();

        // then
        assertThat(history.getUser()).isEqualTo(user);
        assertThat(history.getPreviousName()).isEqualTo(previousName);
        assertThat(history.getNewName()).isEqualTo(newName);
        assertThat(history.getChangedAt()).isEqualTo(changedAt);
    }
}

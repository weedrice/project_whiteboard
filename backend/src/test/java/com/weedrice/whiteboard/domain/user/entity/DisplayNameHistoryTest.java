package com.weedrice.whiteboard.domain.user.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DisplayNameHistoryTest {

    @Test
    @DisplayName("DisplayNameHistory 생성 성공")
    void createDisplayNameHistory_success() {
        // given
        User user = User.builder().build();
        String previousName = "OldName";
        String newName = "NewName";

        // when
        DisplayNameHistory history = DisplayNameHistory.builder()
                .user(user)
                .previousName(previousName)
                .newName(newName)
                .build();

        // then
        assertThat(history.getUser()).isEqualTo(user);
        assertThat(history.getPreviousName()).isEqualTo(previousName);
        assertThat(history.getNewName()).isEqualTo(newName);
    }
}

package com.weedrice.whiteboard.domain.emoticon.entity;

import com.weedrice.whiteboard.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EmoticonPurchase 엔티티 테스트")
class EmoticonPurchaseTest {

    @Test
    @DisplayName("builder - 모든 필드 설정")
    void builder_allFields() {
        User user = User.builder().loginId("u").displayName("U").email("e@e.com").password("p").build();
        ReflectionTestUtils.setField(user, "userId", 1L);
        EmoticonMaster emoticon = EmoticonMaster.builder()
                .name("이모티콘")
                .thumbnailUrl("url")
                .tags(java.util.Collections.emptyList())
                .creator(user)
                .build();
        ReflectionTestUtils.setField(emoticon, "emoticonId", 10L);

        EmoticonPurchase purchase = EmoticonPurchase.builder()
                .user(user)
                .emoticon(emoticon)
                .purchasedPrice(100)
                .build();

        assertThat(purchase.getUser()).isSameAs(user);
        assertThat(purchase.getEmoticon()).isSameAs(emoticon);
        assertThat(purchase.getPurchasedPrice()).isEqualTo(100);
    }
}

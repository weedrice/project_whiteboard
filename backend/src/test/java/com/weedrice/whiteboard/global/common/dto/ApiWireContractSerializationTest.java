package com.weedrice.whiteboard.global.common.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.weedrice.whiteboard.domain.admin.dto.AdminResponse;
import com.weedrice.whiteboard.domain.admin.dto.SuperAdminResponse;
import com.weedrice.whiteboard.domain.admin.dto.SuperAdminUpdateResponse;
import com.weedrice.whiteboard.domain.emoticon.dto.EmoticonMasterDto;
import com.weedrice.whiteboard.domain.feed.dto.FeedResponse;
import com.weedrice.whiteboard.domain.post.dto.PostSummary;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ApiWireContractSerializationTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @Test
    void pageResponseKeepsBackendPaginationFieldNames() {
        PageResponse<String> response = new PageResponse<>(
                new PageImpl<>(List.of("item"), PageRequest.of(1, 1), 3));

        JsonNode json = objectMapper.valueToTree(response);

        assertThat(json.get("page").asInt()).isEqualTo(1);
        assertThat(json.get("hasNext").asBoolean()).isTrue();
        assertThat(json.get("hasPrevious").asBoolean()).isTrue();
        assertThat(json.has("number")).isFalse();
        assertThat(json.has("last")).isFalse();
    }

    @Test
    void postSummaryKeepsLegacyListBooleanFieldNames() {
        PostSummary response = PostSummary.builder()
                .isNotice(true)
                .isNsfw(true)
                .isSpoiler(true)
                .isLiked(true)
                .isScrapped(true)
                .isSubscribed(true)
                .build();

        JsonNode json = objectMapper.valueToTree(response);

        assertThat(json.get("notice").asBoolean()).isTrue();
        assertThat(json.get("nsfw").asBoolean()).isTrue();
        assertThat(json.get("isSpoiler").asBoolean()).isTrue();
        assertThat(json.get("liked").asBoolean()).isTrue();
        assertThat(json.get("scrapped").asBoolean()).isTrue();
        assertThat(json.get("subscribed").asBoolean()).isTrue();
        assertThat(json.has("isNotice")).isFalse();
        assertThat(json.has("isNsfw")).isFalse();
    }

    @Test
    void adminAndFeedResponsesKeepLegacyBooleanFieldNames() {
        AdminResponse admin = AdminResponse.builder().isActive(true).build();
        SuperAdminResponse superAdmin = SuperAdminResponse.builder().isSuperAdmin(true).build();
        SuperAdminUpdateResponse superAdminUpdate = SuperAdminUpdateResponse.builder()
                .loginId("admin")
                .isSuperAdmin(true)
                .build();
        FeedResponse.FeedSummary feed = FeedResponse.FeedSummary.builder().isRead(true).build();

        JsonNode adminJson = objectMapper.valueToTree(admin);
        JsonNode superAdminJson = objectMapper.valueToTree(superAdmin);
        JsonNode superAdminUpdateJson = objectMapper.valueToTree(superAdminUpdate);
        JsonNode feedJson = objectMapper.valueToTree(feed);

        assertThat(adminJson.get("active").asBoolean()).isTrue();
        assertThat(adminJson.has("isActive")).isFalse();
        assertThat(superAdminJson.get("superAdmin").asBoolean()).isTrue();
        assertThat(superAdminJson.has("isSuperAdmin")).isFalse();
        assertThat(superAdminUpdateJson.get("superAdmin").asBoolean()).isTrue();
        assertThat(superAdminUpdateJson.has("isSuperAdmin")).isFalse();
        assertThat(feedJson.get("read").asBoolean()).isTrue();
        assertThat(feedJson.has("isRead")).isFalse();
    }

    @Test
    void imageLessEmoticonKeepsNullImagesOnTheWire() {
        EmoticonMasterDto response = EmoticonMasterDto.builder()
                .emoticonId(1L)
                .name("pack")
                .images(null)
                .build();

        JsonNode json = objectMapper.valueToTree(response);

        assertThat(json.has("images")).isTrue();
        assertThat(json.get("images").isNull()).isTrue();
    }
}

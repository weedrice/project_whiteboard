package com.weedrice.whiteboard.global.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Locale;
import java.util.Properties;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionTest {

    @Test
    @DisplayName("BusinessException 생성 테스트")
    void createBusinessException() {
        BusinessException exception = new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        assertThat(exception.getMessage()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE.getMessage());
    }

    @Test
    @DisplayName("BusinessException 커스텀 메시지 생성 테스트")
    void createBusinessExceptionWithCustomMessage() {
        BusinessException exception = new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "Custom Message");
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        assertThat(exception.getMessage()).isEqualTo("Custom Message");
    }

    @Test
    @DisplayName("ErrorCode 속성 테스트")
    void errorCodeProperties() {
        ErrorCode errorCode = ErrorCode.INVALID_INPUT_VALUE;
        assertThat(errorCode.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(errorCode.getCode()).isEqualTo("C001");
        assertThat(errorCode.getMessage()).isEqualTo("error.common.invalidInput");
    }

    @Test
    @DisplayName("User not found ErrorCode properties")
    void userNotFoundErrorCodeProperties() {
        ErrorCode errorCode = ErrorCode.USER_NOT_FOUND;
        assertThat(errorCode.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(errorCode.getCode()).isEqualTo("U001");
        assertThat(errorCode.getMessage()).isEqualTo("error.user.notFound");
    }

    @Test
    @DisplayName("Every ErrorCode message key exists in Korean and English resources")
    void errorCodeMessageKeysExistInMessageResources() throws Exception {
        Properties koreanMessages = loadMessages("messages.properties");
        Properties englishMessages = loadMessages("messages_en.properties");

        for (ErrorCode errorCode : ErrorCode.values()) {
            assertThat(koreanMessages)
                    .as("Korean message key for %s", errorCode.name())
                    .containsKey(errorCode.getMessage());
            assertThat(englishMessages)
                    .as("English message key for %s", errorCode.name())
                    .containsKey(errorCode.getMessage());
        }
    }

    @Test
    @DisplayName("Emoticon image limit messages include the configured limit in Korean and English")
    void emoticonImageLimitMessages_includeConfiguredLimit() throws Exception {
        String key = ErrorCode.EMOTICON_IMAGE_LIMIT_EXCEEDED.getMessage();
        String koreanPattern = loadMessages("messages.properties").getProperty(key);
        String englishPattern = loadMessages("messages_en.properties").getProperty(key);

        assertThat(new MessageFormat(koreanPattern, Locale.KOREAN).format(new Object[]{35}))
                .contains("35");
        assertThat(new MessageFormat(englishPattern, Locale.ENGLISH).format(new Object[]{35}))
                .contains("35");
        assertThat(ErrorCode.EMOTICON_DUPLICATE_IMAGE_FILE.getCode()).isEqualTo("EM007");
    }

    @Test
    @DisplayName("Every ErrorCode code value is unique")
    void errorCodeValuesAreUnique() {
        assertThat(Arrays.stream(ErrorCode.values())
                .collect(Collectors.groupingBy(ErrorCode::getCode)))
                .allSatisfy((code, errorCodes) -> assertThat(errorCodes)
                        .as("ErrorCode code %s", code)
                        .hasSize(1));
    }

    @Test
    void japaneseMessagesCoverEveryKeyAndPreserveInterpolation() throws Exception {
        Properties korean = loadMessages("messages.properties");
        Properties japanese = loadMessages("messages_ja.properties");
        assertThat(japanese.stringPropertyNames()).isEqualTo(korean.stringPropertyNames());
        var parameterPattern = java.util.regex.Pattern.compile("\\{([0-9]+)(?:[,}])");
        for (String key : korean.stringPropertyNames()) {
            String translated = japanese.getProperty(key);
            assertThat(translated).as(key).isNotBlank().doesNotContainPattern("[가-힣]");
            assertThat(parameterPattern.matcher(translated).results().map(match -> match.group(1)).sorted().toList())
                    .as("Interpolation for %s", key)
                    .isEqualTo(parameterPattern.matcher(korean.getProperty(key)).results()
                            .map(match -> match.group(1)).sorted().toList());
            new MessageFormat(translated, Locale.JAPANESE).format(new Object[]{"Alice", "https://example.com"});
        }
    }

    @Test
    void japaneseRegionalRequestsUseJapaneseMessagesAndUnknownLocalesKeepKoreanFallback() {
        var source = new com.weedrice.whiteboard.global.config.MessageConfig().messageSource();
        var request = new org.springframework.mock.web.MockHttpServletRequest();
        request.addHeader("Accept-Language", "ja-JP,ja;q=0.9,en;q=0.8");
        var locale = new org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver().resolveLocale(request);
        assertThat(source.getMessage("error.user.notFound", null, locale)).isEqualTo("ユーザーが見つかりません。");
        assertThat(source.getMessage("mail.verification.body", new Object[]{"123456"}, locale))
                .contains("メール認証コード", "<h3>123456</h3>");
        assertThat(source.getMessage("error.user.notFound", null, Locale.FRENCH))
                .isEqualTo(source.getMessage("error.user.notFound", null, Locale.KOREAN));
    }

    private Properties loadMessages(String resourceName) throws Exception {
        Properties properties = new Properties();
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            assertThat(inputStream).as("%s resource", resourceName).isNotNull();
            properties.load(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        }
        return properties;
    }
}

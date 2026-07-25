package com.weedrice.whiteboard.domain.file.service;

import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileUploadValidationPolicyTest {

    private final FileUploadValidationPolicy policy = new FileUploadValidationPolicy();

    @Test
    void validate_acceptsMaximumDimensionAtPixelLimit() {
        var result = policy.validate(png(10_000, 5_000));

        assertThat(result.width()).isEqualTo(10_000);
        assertThat(result.height()).isEqualTo(5_000);
        assertThat(result.imageFormat()).isEqualToIgnoringCase("png");
    }

    @Test
    void validate_rejectsDimensionAboveMaximum() {
        assertInvalid(png(FileUploadValidationPolicy.MAX_IMAGE_DIMENSION + 1, 1));
    }

    @Test
    void validate_rejectsPixelCountAboveMaximum() {
        assertInvalid(png(10_000, 5_001));
    }

    @Test
    void validate_rejectsSignatureOnlyMalformedImage() {
        assertInvalid(new MockMultipartFile(
                "file", "broken.png", "image/png",
                new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a}));
    }

    private void assertInvalid(MockMultipartFile file) {
        assertThatThrownBy(() -> policy.validate(file))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_FILE_TYPE);
    }

    private MockMultipartFile png(int width, int height) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                DataOutputStream output = new DataOutputStream(bytes)) {
            output.write(new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a});
            byte[] ihdr = new byte[13];
            java.nio.ByteBuffer.wrap(ihdr)
                    .putInt(width)
                    .putInt(height)
                    .put((byte) 8)
                    .put((byte) 2)
                    .put((byte) 0)
                    .put((byte) 0)
                    .put((byte) 0);
            writeChunk(output, "IHDR", ihdr);
            writeChunk(output, "IEND", new byte[0]);
            return new MockMultipartFile("file", "image.png", "image/png", bytes.toByteArray());
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private void writeChunk(DataOutputStream output, String type, byte[] data) throws IOException {
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        output.writeInt(data.length);
        output.write(typeBytes);
        output.write(data);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        output.writeInt((int) crc.getValue());
    }

    @Test
    void validate_rejectsFileAboveTargetSizeLimit() {
        // 스페이스 아이콘은 2MiB가 상한이다. 전역 상한(10MB)만 보면 통과하던 크기다.
        MockMultipartFile oversized = new MockMultipartFile(
                "file", "icon.png", "image/png", new byte[3 * 1024 * 1024]);

        assertThatThrownBy(() -> policy.validate(oversized, FileUploadTarget.BOARD_ICON))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.FILE_TOO_LARGE);
    }

    @Test
    void validate_allowsSameFileForGenericTarget() {
        // 대상을 지정하지 않으면 기존 동작을 유지한다. 3MiB는 전역 상한 아래라 크기 검사를 통과하고,
        // 이후 이미지 형식 검사에서 걸린다. 즉 크기 때문에 막힌 것이 아님을 확인한다.
        MockMultipartFile file = new MockMultipartFile(
                "file", "icon.png", "image/png", new byte[3 * 1024 * 1024]);

        assertThatThrownBy(() -> policy.validate(file, FileUploadTarget.GENERIC))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_FILE_TYPE);
    }

    @Test
    void validate_rejectsImageAboveTargetDimensionLimit() {
        // 프로필 이미지는 512x512가 상한이다. 전역 상한(16384)만 보면 통과하던 해상도다.
        assertThatThrownBy(() -> policy.validate(png(1024, 1024), FileUploadTarget.PROFILE_IMAGE))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_FILE_TYPE);
    }

    @Test
    void validate_acceptsImageWithinTargetDimensionLimit() {
        assertThat(policy.validate(png(256, 256), FileUploadTarget.PROFILE_IMAGE).width()).isEqualTo(256);
    }

    @Test
    void validate_appliesNoTargetDimensionLimitWhenTargetDeclaresNone() {
        // 게시글 본문 이미지는 대상별 해상도 제한이 없어 전역 상한만 적용된다.
        assertThat(policy.validate(png(1024, 1024), FileUploadTarget.POST_CONTENT).height()).isEqualTo(1024);
    }

    @Test
    void validate_treatsUnknownTargetAsGeneric() {
        assertThat(FileUploadTarget.from("does-not-exist")).isEqualTo(FileUploadTarget.GENERIC);
        assertThat(FileUploadTarget.from(null)).isEqualTo(FileUploadTarget.GENERIC);
        assertThat(FileUploadTarget.from(" board_icon ")).isEqualTo(FileUploadTarget.BOARD_ICON);
    }
}

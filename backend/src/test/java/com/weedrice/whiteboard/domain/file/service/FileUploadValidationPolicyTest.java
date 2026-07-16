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
}

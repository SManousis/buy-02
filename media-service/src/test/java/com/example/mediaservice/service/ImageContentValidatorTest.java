package com.example.mediaservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.zip.CRC32;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class ImageContentValidatorTest {

    private static final int MAX_FILE_SIZE = 2 * 1024 * 1024;
    private static final byte[] WEBP_1X1 = Base64.getDecoder().decode(
            "UklGRiQAAABXRUJQVlA4IBgAAAAwAQCdASoBAAEAAgA0JaQAA3AA/vv9UAA=");

    private final ImageContentValidator validator = new ImageContentValidator();

    @Test
    void acceptsAndIdentifiesDecodedJpegPngAndWebpImages() throws Exception {
        assertDetected("photo.jpg", "image/jpeg", encodedImage("jpeg"), "image/jpeg", "jpg");
        assertDetected("photo.png", "image/png", encodedImage("png"), "image/png", "png");
        assertDetected("photo.webp", "image/webp", WEBP_1X1, "image/webp", "webp");
    }

    @Test
    void acceptsAValidImageAtTheExactTwoMiBBoundary() throws Exception {
        byte[] exactBoundaryPng = pngAtExactSize(MAX_FILE_SIZE);

        ValidatedImage image = validator.validate(file("boundary.png", "image/png", exactBoundaryPng));

        assertThat(image.content()).hasSize(MAX_FILE_SIZE);
        assertThat(image.contentType()).isEqualTo("image/png");
    }

    @Test
    void rejectsAnImageOneByteOverTheLimit() {
        byte[] oversized = new byte[MAX_FILE_SIZE + 1];

        assertThatThrownBy(() -> validator.validate(file("large.png", "image/png", oversized)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2 MB");
    }

    @Test
    void rejectsTextDisguisedAsAnImage() {
        assertThatThrownBy(() -> validator.validate(file(
                "fake.png", "image/png", "not an image".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("valid JPEG, PNG, or WEBP");
    }

    @Test
    void rejectsEmptyExecutableArchiveAndCorruptImageBodies() {
        assertThatThrownBy(() -> validator.validate(file("empty.png", "image/png", new byte[0])))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validate(file(
                "program.png", "image/png", new byte[] {'M', 'Z', (byte) 0x90, 0})))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validate(file(
                "archive.webp", "image/webp", new byte[] {'P', 'K', 3, 4})))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validate(file(
                "corrupt.jpg", "image/jpeg",
                new byte[] {(byte) 0xff, (byte) 0xd8, 0, (byte) 0xff, (byte) 0xd9})))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validate(file(
                "corrupt.webp", "image/webp", corruptWebpHeader())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsCorruptBytesFoundInStoredContent() {
        assertThatThrownBy(() -> validator.validateStored(
                "damaged".getBytes(StandardCharsets.UTF_8), "image/png"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsDeclaredMimeTypeThatDoesNotMatchDetectedContent() throws Exception {
        assertThatThrownBy(() -> validator.validate(file("photo.jpg", "image/jpeg", encodedImage("png"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match");
    }

    @Test
    void rejectsCorruptAndTrailingPolyglotContent() throws Exception {
        byte[] png = encodedImage("png");
        byte[] withTrailingScript = Arrays.copyOf(png, png.length + 8);
        System.arraycopy("<script>".getBytes(StandardCharsets.UTF_8), 0,
                withTrailingScript, png.length, 8);

        assertThatThrownBy(() -> validator.validate(file(
                "corrupt.png", "image/png", new byte[] {(byte) 0x89, 'P', 'N', 'G'})))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validate(file(
                "polyglot.png", "image/png", withTrailingScript)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("trailing");

        byte[] disguisedPngTrailer = Arrays.copyOf(withTrailingScript, withTrailingScript.length + 12);
        System.arraycopy(png, png.length - 12, disguisedPngTrailer, withTrailingScript.length, 12);
        assertThatThrownBy(() -> validator.validate(file(
                "polyglot-with-fake-end.png", "image/png", disguisedPngTrailer)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("trailing");

        byte[] jpeg = encodedImage("jpeg");
        byte[] disguisedJpegTrailer = Arrays.copyOf(jpeg, jpeg.length + 10);
        System.arraycopy("<script>".getBytes(StandardCharsets.UTF_8), 0,
                disguisedJpegTrailer, jpeg.length, 8);
        disguisedJpegTrailer[disguisedJpegTrailer.length - 2] = (byte) 0xff;
        disguisedJpegTrailer[disguisedJpegTrailer.length - 1] = (byte) 0xd9;
        assertThatThrownBy(() -> validator.validate(file(
                "polyglot.jpg", "image/jpeg", disguisedJpegTrailer)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("trailing");
    }

    private void assertDetected(String filename, String declaredType, byte[] content,
                                String expectedType, String expectedExtension) {
        ValidatedImage image = validator.validate(file(filename, declaredType, content));

        assertThat(image.contentType()).isEqualTo(expectedType);
        assertThat(image.extension()).isEqualTo(expectedExtension);
        assertThat(image.content()).isEqualTo(content);
    }

    private MockMultipartFile file(String filename, String contentType, byte[] content) {
        return new MockMultipartFile("file", filename, contentType, content);
    }

    private byte[] corruptWebpHeader() {
        return new byte[] {
                'R', 'I', 'F', 'F', 8, 0, 0, 0,
                'W', 'E', 'B', 'P', 'V', 'P', '8', ' '
        };
    }

    private byte[] encodedImage(String format) throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.BLUE);
        graphics.fillRect(0, 0, 2, 2);
        graphics.dispose();

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertThat(ImageIO.write(image, format, output)).isTrue();
        return output.toByteArray();
    }

    private byte[] pngAtExactSize(int targetSize) throws Exception {
        byte[] basePng = encodedImage("png");
        int iendSize = 12;
        int paddingLength = targetSize - basePng.length - 12;
        byte[] chunkType = "paDd".getBytes(StandardCharsets.US_ASCII);
        byte[] padding = new byte[paddingLength];
        CRC32 crc = new CRC32();
        crc.update(chunkType);
        crc.update(padding);

        ByteArrayOutputStream output = new ByteArrayOutputStream(targetSize);
        output.write(basePng, 0, basePng.length - iendSize);
        DataOutputStream data = new DataOutputStream(output);
        data.writeInt(paddingLength);
        data.write(chunkType);
        data.write(padding);
        data.writeInt((int) crc.getValue());
        output.write(basePng, basePng.length - iendSize, iendSize);

        assertThat(output.size()).isEqualTo(targetSize);
        return output.toByteArray();
    }
}

package com.example.mediaservice.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Locale;
import java.util.zip.CRC32;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class ImageContentValidator {

    private static final int MAX_FILE_SIZE = 2 * 1024 * 1024;
    private static final long MAX_PIXELS = 16_000_000L;
    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'
    };

    public ValidatedImage validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Image file must not be empty");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("Image size must not exceed 2 MB");
        }

        final byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException exception) {
            throw new IllegalArgumentException("Could not read the uploaded image", exception);
        }

        return validateContent(content, file.getContentType());
    }

    public ValidatedImage validateStored(byte[] content, String expectedContentType) {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("Stored image content must not be empty");
        }
        return validateContent(content, expectedContentType);
    }

    private ValidatedImage validateContent(byte[] content, String declaredContentType) {
        if (content.length > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("Image size must not exceed 2 MB");
        }

        ImageType detectedType = detectType(content);
        String declaredType = normalizeContentType(declaredContentType);
        if (!detectedType.contentType().equals(declaredType)) {
            throw new IllegalArgumentException("Declared image type does not match the file content");
        }

        rejectTrailingContent(content, detectedType);
        verifyDecodable(content);
        return new ValidatedImage(content, detectedType.contentType(), detectedType.extension());
    }

    private ImageType detectType(byte[] content) {
        if (startsWith(content, PNG_SIGNATURE)) {
            return ImageType.PNG;
        }
        if (content.length >= 4
                && unsigned(content[0]) == 0xff && unsigned(content[1]) == 0xd8
                && unsigned(content[content.length - 2]) == 0xff
                && unsigned(content[content.length - 1]) == 0xd9) {
            return ImageType.JPEG;
        }
        if (content.length >= 16
                && asciiEquals(content, 0, "RIFF")
                && asciiEquals(content, 8, "WEBP")
                && isSupportedWebpChunk(content)) {
            return ImageType.WEBP;
        }
        throw new IllegalArgumentException("File content is not a valid JPEG, PNG, or WEBP image");
    }

    private void rejectTrailingContent(byte[] content, ImageType type) {
        if (type == ImageType.PNG) {
            validatePngChunks(content);
        } else if (type == ImageType.JPEG) {
            validateJpegEndMarker(content);
        } else if (type == ImageType.WEBP) {
            long declaredFileSize = readLittleEndianUnsignedInt(content, 4) + 8;
            if (declaredFileSize != content.length) {
                throw new IllegalArgumentException("WEBP image has invalid or trailing content");
            }
        }
    }

    private void validatePngChunks(byte[] content) {
        int offset = PNG_SIGNATURE.length;
        while (offset < content.length) {
            if (content.length - offset < 12) {
                throw invalidOrTrailing("PNG");
            }

            long dataLength = readBigEndianUnsignedInt(content, offset);
            long nextOffset = (long) offset + 12 + dataLength;
            if (nextOffset > content.length) {
                throw invalidOrTrailing("PNG");
            }

            int typeOffset = offset + 4;
            CRC32 crc = new CRC32();
            crc.update(content, typeOffset, Math.toIntExact(4 + dataLength));
            long expectedCrc = readBigEndianUnsignedInt(content, Math.toIntExact(nextOffset - 4));
            if (crc.getValue() != expectedCrc) {
                throw invalidOrTrailing("PNG");
            }

            if (asciiEquals(content, typeOffset, "IEND")) {
                if (dataLength != 0 || nextOffset != content.length) {
                    throw invalidOrTrailing("PNG");
                }
                return;
            }
            offset = Math.toIntExact(nextOffset);
        }
        throw invalidOrTrailing("PNG");
    }

    private void validateJpegEndMarker(byte[] content) {
        int offset = 2;
        boolean entropyData = false;
        while (offset < content.length) {
            if (entropyData) {
                if (unsigned(content[offset++]) != 0xff) {
                    continue;
                }
                while (offset < content.length && unsigned(content[offset]) == 0xff) {
                    offset++;
                }
                if (offset >= content.length) {
                    throw invalidOrTrailing("JPEG");
                }
                int marker = unsigned(content[offset]);
                if (marker == 0x00 || marker >= 0xd0 && marker <= 0xd7) {
                    offset++;
                    continue;
                }
                entropyData = false;
                offset--;
                continue;
            }

            if (unsigned(content[offset++]) != 0xff) {
                throw invalidOrTrailing("JPEG");
            }
            while (offset < content.length && unsigned(content[offset]) == 0xff) {
                offset++;
            }
            if (offset >= content.length) {
                throw invalidOrTrailing("JPEG");
            }

            int marker = unsigned(content[offset++]);
            if (marker == 0xd9) {
                if (offset != content.length) {
                    throw invalidOrTrailing("JPEG");
                }
                return;
            }
            if (marker == 0xd8 || marker == 0x01 || marker >= 0xd0 && marker <= 0xd7) {
                continue;
            }
            if (offset + 2 > content.length) {
                throw invalidOrTrailing("JPEG");
            }
            int segmentLength = unsigned(content[offset]) << 8 | unsigned(content[offset + 1]);
            if (segmentLength < 2 || offset + segmentLength > content.length) {
                throw invalidOrTrailing("JPEG");
            }
            offset += segmentLength;
            entropyData = marker == 0xda;
        }
        throw invalidOrTrailing("JPEG");
    }

    private IllegalArgumentException invalidOrTrailing(String format) {
        return new IllegalArgumentException(format + " image has invalid or trailing content");
    }

    private void verifyDecodable(byte[] content) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
            if (input == null) {
                throw invalidImage(null);
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw invalidImage(null);
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > MAX_PIXELS) {
                    throw new IllegalArgumentException("Image dimensions are invalid or too large");
                }
                BufferedImage image = reader.read(0);
                if (image == null) {
                    throw invalidImage(null);
                }
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof IllegalArgumentException illegalArgumentException) {
                throw illegalArgumentException;
            }
            throw invalidImage(exception);
        }
    }

    private IllegalArgumentException invalidImage(Throwable cause) {
        return new IllegalArgumentException("File content is not a valid JPEG, PNG, or WEBP image", cause);
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null) {
            return "";
        }
        int parametersStart = contentType.indexOf(';');
        String baseType = parametersStart >= 0 ? contentType.substring(0, parametersStart) : contentType;
        return baseType.trim().toLowerCase(Locale.ROOT);
    }

    private boolean startsWith(byte[] content, byte[] prefix) {
        if (content.length < prefix.length) {
            return false;
        }
        for (int index = 0; index < prefix.length; index++) {
            if (content[index] != prefix[index]) {
                return false;
            }
        }
        return true;
    }

    private boolean asciiEquals(byte[] content, int offset, String expected) {
        if (offset < 0 || content.length < offset + expected.length()) {
            return false;
        }
        byte[] expectedBytes = expected.getBytes(StandardCharsets.US_ASCII);
        for (int index = 0; index < expectedBytes.length; index++) {
            if (content[offset + index] != expectedBytes[index]) {
                return false;
            }
        }
        return true;
    }

    private boolean isSupportedWebpChunk(byte[] content) {
        return asciiEquals(content, 12, "VP8 ")
                || asciiEquals(content, 12, "VP8L")
                || asciiEquals(content, 12, "VP8X");
    }

    private long readBigEndianUnsignedInt(byte[] content, int offset) {
        return (long) unsigned(content[offset]) << 24
                | (long) unsigned(content[offset + 1]) << 16
                | (long) unsigned(content[offset + 2]) << 8
                | unsigned(content[offset + 3]);
    }

    private long readLittleEndianUnsignedInt(byte[] content, int offset) {
        return unsigned(content[offset])
                | (long) unsigned(content[offset + 1]) << 8
                | (long) unsigned(content[offset + 2]) << 16
                | (long) unsigned(content[offset + 3]) << 24;
    }

    private int unsigned(byte value) {
        return value & 0xff;
    }

    private enum ImageType {
        JPEG("image/jpeg", "jpg"),
        PNG("image/png", "png"),
        WEBP("image/webp", "webp");

        private final String contentType;
        private final String extension;

        ImageType(String contentType, String extension) {
            this.contentType = contentType;
            this.extension = extension;
        }

        String contentType() {
            return contentType;
        }

        String extension() {
            return extension;
        }
    }
}

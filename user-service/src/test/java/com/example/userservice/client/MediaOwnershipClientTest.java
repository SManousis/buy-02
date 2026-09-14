package com.example.userservice.client;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.userservice.config.AppProperties;
import com.example.userservice.exception.InvalidAvatarMediaException;
import com.example.userservice.exception.MediaServiceUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

class MediaOwnershipClientTest {

    private MockRestServiceServer server;
    private MediaOwnershipClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new MediaOwnershipClient(builder, properties());
    }

    @Test
    void forwardsBearerTokenToDiscoveryBackedMetadataEndpoint() {
        server.expect(requestTo("http://media-service/media/images/media-1/metadata"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token"))
                .andRespond(withSuccess(metadata("seller-1", "image/png"), MediaType.APPLICATION_JSON));

        client.verifyOwnedImage("seller-1", "media-1", "token");

        server.verify();
    }

    @Test
    void rejectsForeignOrMissingMedia() {
        server.expect(requestTo("http://media-service/media/images/media-1/metadata"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> client.verifyOwnedImage("seller-1", "media-1", "token"))
                .isInstanceOf(InvalidAvatarMediaException.class);
    }

    @Test
    void rejectsNonImageMetadata() {
        server.expect(requestTo("http://media-service/media/images/media-1/metadata"))
                .andRespond(withSuccess(metadata("seller-1", "application/pdf"), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.verifyOwnedImage("seller-1", "media-1", "token"))
                .isInstanceOf(InvalidAvatarMediaException.class);
    }

    @Test
    void mapsConnectivityAndServerFailuresToRetryableException() {
        server.expect(requestTo("http://media-service/media/images/media-1/metadata"))
                .andRespond(request -> {
                    throw new ResourceAccessException("connection refused");
                });

        assertThatThrownBy(() -> client.verifyOwnedImage("seller-1", "media-1", "token"))
                .isInstanceOf(MediaServiceUnavailableException.class);

        server.reset();
        server.expect(requestTo("http://media-service/media/images/media-1/metadata"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.verifyOwnedImage("seller-1", "media-1", "token"))
                .isInstanceOf(MediaServiceUnavailableException.class);
    }

    @Test
    void treatsMissingMediaAsAnIdempotentCleanupSuccess() {
        server.expect(requestTo("http://media-service/media/images/media-1"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        client.deleteOwnedImage("media-1", "token");

        server.verify();
    }

    private AppProperties properties() {
        return new AppProperties(
                new AppProperties.JwtProperties(
                        "01234567890123456789012345678901", 60_000, "user-service", "buy-01-api"),
                new AppProperties.CorsProperties(java.util.List.of("http://localhost:4200")),
                new AppProperties.MediaProperties("media-service"));
    }

    private String metadata(String sellerId, String contentType) {
        return "{\"id\":\"media-1\",\"sellerId\":\"" + sellerId
                + "\",\"contentType\":\"" + contentType + "\",\"sizeBytes\":42}";
    }
}

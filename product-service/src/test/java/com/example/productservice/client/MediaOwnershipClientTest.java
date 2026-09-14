package com.example.productservice.client;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import com.example.productservice.config.AppProperties;
import com.example.productservice.exception.InvalidMediaReferenceException;
import com.example.productservice.exception.MediaServiceUnavailableException;

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
    void passesBearerTokenToMetadataEndpoint() {
        server.expect(requestTo("http://media-service/media/images/media-1/metadata"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer seller-token"))
                .andRespond(withSuccess(metadata("seller-1", "image/png"), MediaType.APPLICATION_JSON));

        client.verifyOwnedImage("media-1", "seller-1", "Bearer seller-token");

        server.verify();
    }

    @Test
    void rejectsMetadataOwnedByAnotherSeller() {
        server.expect(requestTo("http://media-service/media/images/media-1/metadata"))
                .andRespond(withSuccess(metadata("seller-2", "image/png"), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.verifyOwnedImage("media-1", "seller-1", "Bearer seller-token"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void rejectsNonImageMetadata() {
        server.expect(requestTo("http://media-service/media/images/media-1/metadata"))
                .andRespond(withSuccess(metadata("seller-1", "application/pdf"), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.verifyOwnedImage("media-1", "seller-1", "Bearer seller-token"))
                .isInstanceOf(InvalidMediaReferenceException.class)
                .hasMessageContaining("not an image");
    }

    @Test
    void mapsMissingMediaToValidationError() {
        server.expect(requestTo("http://media-service/media/images/media-1/metadata"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> client.verifyOwnedImage("media-1", "seller-1", "Bearer seller-token"))
                .isInstanceOf(InvalidMediaReferenceException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void mapsConnectionFailuresToRetryableException() {
        server.expect(requestTo("http://media-service/media/images/media-1/metadata"))
                .andRespond(request -> {
                    throw new ResourceAccessException("connection refused");
                });

        assertThatThrownBy(() -> client.verifyOwnedImage("media-1", "seller-1", "Bearer seller-token"))
                .isInstanceOf(MediaServiceUnavailableException.class);
    }

    @Test
    void mapsDiscoveryWithNoServiceInstanceToRetryableException() {
        RestClient.Builder noInstanceBuilder = RestClient.builder()
                .requestInterceptor((request, body, execution) -> {
                    throw new IllegalStateException("No instances available for media-service");
                });
        MediaOwnershipClient noInstanceClient = new MediaOwnershipClient(noInstanceBuilder, properties());

        assertThatThrownBy(() -> noInstanceClient.verifyOwnedImage(
                "media-1", "seller-1", "Bearer seller-token"))
                .isInstanceOf(MediaServiceUnavailableException.class)
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void mapsAnUpstreamForbiddenResponseToAccessDenied() {
        server.expect(requestTo("http://media-service/media/images/media-1/metadata"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> client.verifyOwnedImage(
                "media-1", "seller-1", "Bearer seller-token"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void mapsMediaServerErrorsToRetryableException() {
        server.expect(requestTo("http://media-service/media/images/media-1/metadata"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.verifyOwnedImage("media-1", "seller-1", "Bearer seller-token"))
                .isInstanceOf(MediaServiceUnavailableException.class);
    }

    private AppProperties properties() {
        return new AppProperties(
                new AppProperties.JwtProperties(
                        "01234567890123456789012345678901", 60_000, "user-service", "buy-01-api"),
                new AppProperties.CorsProperties(java.util.List.of("http://localhost:4200")),
                new AppProperties.KafkaProperties(new AppProperties.KafkaProperties.Topics(
                        "product.created", "product.updated", "product.deleted", "image.deleted")),
                new AppProperties.MediaProperties("http://media-service"));
    }

    private String metadata(String sellerId, String contentType) {
        return "{\"id\":\"media-1\",\"sellerId\":\"" + sellerId
                + "\",\"contentType\":\"" + contentType + "\",\"sizeBytes\":42}";
    }
}

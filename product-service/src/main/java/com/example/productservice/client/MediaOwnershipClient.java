package com.example.productservice.client;

import org.springframework.http.HttpHeaders;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.example.productservice.config.AppProperties;
import com.example.productservice.exception.InvalidMediaReferenceException;
import com.example.productservice.exception.MediaServiceUnavailableException;

@Component
public class MediaOwnershipClient {

    private final RestClient restClient;

    public MediaOwnershipClient(RestClient.Builder restClientBuilder, AppProperties appProperties) {
        this.restClient = restClientBuilder
                .baseUrl(appProperties.media().baseUrl())
                .build();
    }

    public void verifyOwnedImage(String imageId, String sellerId, String bearerToken) {
        MediaMetadataResponse metadata;
        try {
            metadata = restClient.get()
                    .uri("/media/images/{id}/metadata", imageId)
                    .header(HttpHeaders.AUTHORIZATION, bearerToken)
                    .retrieve()
                    .body(MediaMetadataResponse.class);
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            if (status == 404) {
                throw new InvalidMediaReferenceException("Image " + imageId + " was not found");
            }
            if (status == 403) {
                throw new AccessDeniedException("You do not own this media asset");
            }
            throw new MediaServiceUnavailableException("Media validation is temporarily unavailable", exception);
        } catch (RestClientException | IllegalStateException exception) {
            throw new MediaServiceUnavailableException("Media validation is temporarily unavailable", exception);
        }

        if (metadata == null || metadata.id() == null || !imageId.equals(metadata.id())) {
            throw new InvalidMediaReferenceException("Image " + imageId + " was not found");
        }
        if (!sellerId.equals(metadata.sellerId())) {
            throw new AccessDeniedException("You do not own this media asset");
        }
        if (metadata.contentType() == null || !metadata.contentType().startsWith("image/")) {
            throw new InvalidMediaReferenceException("Media " + imageId + " is not an image");
        }
    }

    private record MediaMetadataResponse(String id, String sellerId, String contentType, long sizeBytes) {}
}

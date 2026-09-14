package com.example.userservice.client;

import com.example.userservice.config.AppProperties;
import com.example.userservice.dto.MediaMetadataResponse;
import com.example.userservice.exception.InvalidAvatarMediaException;
import com.example.userservice.exception.MediaServiceUnavailableException;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class MediaOwnershipClient {

    private final RestClient restClient;
    private final String mediaServiceBaseUrl;

    public MediaOwnershipClient(
            @Qualifier("loadBalancedRestClientBuilder") RestClient.Builder restClientBuilder,
            AppProperties properties) {
        this.restClient = restClientBuilder.build();
        this.mediaServiceBaseUrl = "http://" + properties.media().serviceName();
    }

    public void verifyOwnedImage(String userId, String mediaId, String bearerToken) {
        MediaMetadataResponse metadata;
        try {
            metadata = restClient.get()
                    .uri(mediaServiceBaseUrl + "/media/images/{id}/metadata", mediaId)
                    .headers(headers -> headers.setBearerAuth(bearerToken))
                    .retrieve()
                    .body(MediaMetadataResponse.class);
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            if (status == 403 || status == 404) {
                throw new InvalidAvatarMediaException("Invalid avatar media reference");
            }
            throw new MediaServiceUnavailableException(
                    "Media validation is temporarily unavailable", exception);
        } catch (RestClientException | IllegalStateException exception) {
            throw new MediaServiceUnavailableException(
                    "Media validation is temporarily unavailable", exception);
        }

        if (metadata == null
                || !mediaId.equals(metadata.id())
                || !userId.equals(metadata.sellerId())
                || metadata.contentType() == null
                || !metadata.contentType().startsWith("image/")) {
            throw new InvalidAvatarMediaException("Invalid avatar media reference");
        }
    }

    public void deleteOwnedImage(String mediaId, String bearerToken) {
        try {
            restClient.delete()
                    .uri(mediaServiceBaseUrl + "/media/images/{id}", mediaId)
                    .headers(headers -> headers.setBearerAuth(bearerToken))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404) {
                return;
            }
            throw new MediaServiceUnavailableException(
                    "Media cleanup is temporarily unavailable", exception);
        } catch (RestClientException | IllegalStateException exception) {
            throw new MediaServiceUnavailableException(
                    "Media cleanup is temporarily unavailable", exception);
        }
    }
}

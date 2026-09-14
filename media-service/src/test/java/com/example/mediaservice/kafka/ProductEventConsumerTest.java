package com.example.mediaservice.kafka;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.example.mediaservice.exception.MediaNotFoundException;
import com.example.mediaservice.service.MediaService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductEventConsumerTest {

    @Mock
    private MediaService mediaService;

    @Test
    void propagatesCleanupFailuresSoTheKafkaListenerCanRetryTheEvent() {
        IllegalStateException failure = new IllegalStateException("storage unavailable");
        doThrow(failure).when(mediaService).deleteImage("seller-1", "media-1");

        assertThatThrownBy(() -> consumer().onProductDeleted(eventWith("media-1")))
                .isSameAs(failure);
    }

    @Test
    void treatsAlreadyMissingMediaAsAnIdempotentSuccessfulCleanup() {
        doThrow(new MediaNotFoundException("already gone"))
                .when(mediaService).deleteImage("seller-1", "media-1");

        consumer().onProductDeleted(eventWith("media-1", "media-2"));

        verify(mediaService).deleteImage("seller-1", "media-1");
        verify(mediaService).deleteImage("seller-1", "media-2");
    }

    private ProductEventConsumer consumer() {
        return new ProductEventConsumer(mediaService);
    }

    private ProductDeletedEvent eventWith(String... imageIds) {
        return new ProductDeletedEvent(
                "PRODUCT_DELETED", "product-1", "seller-1", List.of(imageIds), null);
    }
}

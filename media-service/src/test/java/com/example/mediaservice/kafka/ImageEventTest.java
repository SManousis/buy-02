package com.example.mediaservice.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ImageEventTest {

    @Test
    void doesNotExposeStorageKeysInCrossServiceEvents() {
        assertThat(ImageEvent.class.getRecordComponents())
                .extracting(component -> component.getName())
                .doesNotContain("storageKey");
    }
}

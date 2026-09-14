package com.example.productservice.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.productservice.model.Product;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.UpdateDefinition;

@ExtendWith(MockitoExtension.class)
class ProductImageFieldMigrationTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Test
    void renamesLegacyImageUrlsOnlyWhenCanonicalImageIdsAreAbsent() {
        when(mongoTemplate.updateMulti(any(Query.class), any(UpdateDefinition.class), eq(Product.class)))
                .thenReturn(UpdateResult.acknowledged(2, 2L, null));

        new ProductImageFieldMigration(mongoTemplate).migrateLegacyImageUrls();

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<UpdateDefinition> updateCaptor = ArgumentCaptor.forClass(UpdateDefinition.class);
        verify(mongoTemplate).updateMulti(queryCaptor.capture(), updateCaptor.capture(), eq(Product.class));

        assertThat(queryCaptor.getValue().getQueryObject())
                .containsEntry("imageUrls", new Document("$exists", true))
                .containsEntry("imageIds", new Document("$exists", false));
        assertThat(updateCaptor.getValue().getUpdateObject())
                .isEqualTo(new Document("$rename", new Document("imageUrls", "imageIds")));
    }
}

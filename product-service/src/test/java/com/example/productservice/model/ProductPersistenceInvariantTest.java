package com.example.productservice.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

class ProductPersistenceInvariantTest {

    @Test
    void mapsImageIdsToAUniqueMongoIndexThatExcludesEmptyLists() {
        MongoMappingContext mappingContext = new MongoMappingContext();
        mappingContext.setSimpleTypeHolder(
                MongoCustomConversions.create(adapter -> {}).getSimpleTypeHolder());
        mappingContext.afterPropertiesSet();
        MongoPersistentEntityIndexResolver resolver =
                new MongoPersistentEntityIndexResolver(mappingContext);

        List<MongoPersistentEntityIndexResolver.IndexDefinitionHolder> indexes =
                resolver.resolveIndexForEntity(mappingContext.getRequiredPersistentEntity(Product.class));

        MongoPersistentEntityIndexResolver.IndexDefinitionHolder imageIdsIndex = indexes.stream()
                .filter(index -> index.getIndexKeys().containsKey("imageIds"))
                .findFirst()
                .orElseThrow();
        assertThat(imageIdsIndex.getIndexOptions())
                .containsEntry("unique", true)
                .containsEntry("partialFilterExpression",
                        new Document("imageIds.0", new Document("$exists", true)))
                .doesNotContainKey("sparse");
    }
}

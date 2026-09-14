package com.example.productservice.migration;

import com.example.productservice.model.Product;
import com.mongodb.client.result.UpdateResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProductImageFieldMigration implements ApplicationRunner {

    private final MongoTemplate mongoTemplate;

    @Override
    public void run(ApplicationArguments args) {
        migrateLegacyImageUrls();
    }

    void migrateLegacyImageUrls() {
        Query legacyOnly = Query.query(Criteria.where("imageUrls").exists(true)
                .and("imageIds").exists(false));
        UpdateResult result = mongoTemplate.updateMulti(
                legacyOnly,
                new Update().rename("imageUrls", "imageIds"),
                Product.class);
        if (result.getModifiedCount() > 0) {
            log.info("Migrated {} product document(s) from imageUrls to imageIds",
                    result.getModifiedCount());
        }
    }
}

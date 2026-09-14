package com.example.productservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.AccessDeniedException;

import com.example.productservice.client.MediaOwnershipClient;
import com.example.productservice.dto.CreateProductRequest;
import com.example.productservice.dto.ProductResponse;
import com.example.productservice.dto.UpdateProductRequest;
import com.example.productservice.exception.InvalidMediaReferenceException;
import com.example.productservice.kafka.ProductEventProducer;
import com.example.productservice.model.Product;
import com.example.productservice.repository.ProductRepository;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    private static final String SELLER_ID = "seller-1";
    private static final String OTHER_SELLER_ID = "seller-2";
    private static final String BEARER_TOKEN = "Bearer seller-token";

    @Mock
    private ProductRepository productRepository;
    @Mock
    private ProductEventProducer eventProducer;
    @Mock
    private MediaOwnershipClient mediaOwnershipClient;

    private ProductService productService;

    @BeforeEach
    void setUp() {
        productService = new ProductService(productRepository, eventProducer, mediaOwnershipClient);
    }

    @Test
    void createsASellerOwnedProductAndPublishesTheCreatedProduct() {
        when(productRepository.save(org.mockito.ArgumentMatchers.any(Product.class)))
                .thenAnswer(invocation -> {
                    Product saved = invocation.getArgument(0);
                    saved.setId("product-1");
                    return saved;
                });

        ProductResponse response = productService.createProduct(
                SELLER_ID,
                BEARER_TOKEN,
                new CreateProductRequest("  Name  ", "  Description  ", new BigDecimal("10.00"), 1, null));

        assertThat(response)
                .extracting(ProductResponse::id, ProductResponse::sellerId, ProductResponse::name,
                        ProductResponse::description, ProductResponse::price, ProductResponse::stock,
                        ProductResponse::imageIds)
                .containsExactly("product-1", SELLER_ID, "Name", "Description", new BigDecimal("10.00"), 1,
                        List.of());
        ArgumentCaptor<Product> savedProduct = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(savedProduct.capture());
        assertThat(savedProduct.getValue())
                .extracting(Product::getSellerId, Product::getName, Product::getDescription, Product::getImageIds)
                .containsExactly(SELLER_ID, "Name", "Description", List.of());
        verify(eventProducer).publishProductCreated(savedProduct.getValue());
        verifyNoInteractions(mediaOwnershipClient);
    }

    @Test
    void listsAllProductsAsResponses() {
        Product sellerOneProduct = product("product-1", SELLER_ID, "First");
        Product sellerTwoProduct = product("product-2", OTHER_SELLER_ID, "Second");
        when(productRepository.findAll()).thenReturn(List.of(sellerOneProduct, sellerTwoProduct));

        List<ProductResponse> responses = productService.listProducts();

        assertThat(responses)
                .extracting(ProductResponse::id, ProductResponse::sellerId, ProductResponse::name)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("product-1", SELLER_ID, "First"),
                        org.assertj.core.groups.Tuple.tuple("product-2", OTHER_SELLER_ID, "Second"));
    }

    @Test
    void returnsTheProductForItsPublicId() {
        Product stored = product("product-1", SELLER_ID, "Name");
        when(productRepository.findById("product-1")).thenReturn(Optional.of(stored));

        ProductResponse response = productService.getProductById("product-1");

        assertThat(response.id()).isEqualTo("product-1");
        assertThat(response.sellerId()).isEqualTo(SELLER_ID);
        assertThat(response.name()).isEqualTo("Name");
    }

    @Test
    void updatesAnOwningSellersProductAndPublishesTheUpdatedProduct() {
        Product stored = product("product-1", SELLER_ID, "Old name");
        stored.setDescription("Old description");
        stored.setPrice(new BigDecimal("10.00"));
        stored.setStock(1);
        when(productRepository.findByIdAndSellerId("product-1", SELLER_ID)).thenReturn(Optional.of(stored));
        when(productRepository.save(stored)).thenReturn(stored);

        ProductResponse response = productService.updateProduct(
                SELLER_ID,
                BEARER_TOKEN,
                "product-1",
                new UpdateProductRequest("  New name  ", "  New description  ", new BigDecimal("12.50"), 4, null));

        assertThat(response)
                .extracting(ProductResponse::name, ProductResponse::description, ProductResponse::price,
                        ProductResponse::stock)
                .containsExactly("New name", "New description", new BigDecimal("12.50"), 4);
        verify(productRepository).save(stored);
        verify(eventProducer).publishProductUpdated(stored);
        verifyNoInteractions(mediaOwnershipClient);
    }

    @Test
    void deletesAnOwningSellersProductAndPublishesTheDeletedProduct() {
        Product stored = product("product-1", SELLER_ID, "Name");
        when(productRepository.findByIdAndSellerId("product-1", SELLER_ID)).thenReturn(Optional.of(stored));

        productService.deleteProduct(SELLER_ID, "product-1");

        verify(productRepository).delete(stored);
        verify(eventProducer).publishProductDeleted(stored);
    }

    @Test
    void doesNotUpdateAProductOwnedByAnotherSeller() {
        when(productRepository.findByIdAndSellerId("product-1", OTHER_SELLER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.updateProduct(
                OTHER_SELLER_ID,
                BEARER_TOKEN,
                "product-1",
                new UpdateProductRequest("Changed", null, null, null, null)))
                .isInstanceOf(com.example.productservice.exception.NotFoundException.class)
                .hasMessage("Product not found");

        verify(productRepository, never()).save(org.mockito.ArgumentMatchers.any(Product.class));
        verifyNoInteractions(eventProducer, mediaOwnershipClient);
    }

    @Test
    void doesNotDeleteAProductOwnedByAnotherSeller() {
        when(productRepository.findByIdAndSellerId("product-1", OTHER_SELLER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.deleteProduct(OTHER_SELLER_ID, "product-1"))
                .isInstanceOf(com.example.productservice.exception.NotFoundException.class)
                .hasMessage("Product not found");

        verify(productRepository, never()).delete(org.mockito.ArgumentMatchers.any(Product.class));
        verifyNoInteractions(eventProducer, mediaOwnershipClient);
    }

    @Test
    void rejectsDuplicateImageIdsBeforeSaving() {
        CreateProductRequest request = requestWith("media-1", "media-1");

        assertThatThrownBy(() -> productService.createProduct(SELLER_ID, BEARER_TOKEN, request))
                .isInstanceOf(InvalidMediaReferenceException.class)
                .hasMessageContaining("duplicate");

        verifyNoInteractions(mediaOwnershipClient, productRepository, eventProducer);
    }

    @Test
    void rejectsBlankImageIdsBeforeSaving() {
        CreateProductRequest request = requestWith("media-1", "  ");

        assertThatThrownBy(() -> productService.createProduct(SELLER_ID, BEARER_TOKEN, request))
                .isInstanceOf(InvalidMediaReferenceException.class)
                .hasMessageContaining("blank");

        verifyNoInteractions(mediaOwnershipClient, productRepository, eventProducer);
    }

    @Test
    void rejectsMissingImageBeforeSaving() {
        doThrow(new InvalidMediaReferenceException("Image media-1 was not found"))
                .when(mediaOwnershipClient).verifyOwnedImage("media-1", SELLER_ID, BEARER_TOKEN);

        assertThatThrownBy(() -> productService.createProduct(SELLER_ID, BEARER_TOKEN, requestWith("media-1")))
                .isInstanceOf(InvalidMediaReferenceException.class)
                .hasMessageContaining("not found");

        verifyNoInteractions(productRepository, eventProducer);
    }

    @Test
    void rejectsNonImageMediaBeforeSaving() {
        doThrow(new InvalidMediaReferenceException("Media media-1 is not an image"))
                .when(mediaOwnershipClient).verifyOwnedImage("media-1", SELLER_ID, BEARER_TOKEN);

        assertThatThrownBy(() -> productService.createProduct(SELLER_ID, BEARER_TOKEN, requestWith("media-1")))
                .isInstanceOf(InvalidMediaReferenceException.class)
                .hasMessageContaining("not an image");

        verifyNoInteractions(productRepository, eventProducer);
    }

    @Test
    void rejectsAnotherSellersMediaBeforeSaving() {
        doThrow(new AccessDeniedException("You do not own this media asset"))
                .when(mediaOwnershipClient).verifyOwnedImage("media-1", SELLER_ID, BEARER_TOKEN);

        assertThatThrownBy(() -> productService.createProduct(SELLER_ID, BEARER_TOKEN, requestWith("media-1")))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(productRepository, eventProducer);
    }

    @Test
    void validatesChangedImageIdsBeforeSavingAnUpdate() {
        Product product = Product.builder().id("product-1").sellerId(SELLER_ID).build();
        when(productRepository.findByIdAndSellerId("product-1", SELLER_ID)).thenReturn(Optional.of(product));
        doThrow(new InvalidMediaReferenceException("Media media-1 is not an image"))
                .when(mediaOwnershipClient).verifyOwnedImage("media-1", SELLER_ID, BEARER_TOKEN);
        UpdateProductRequest request = new UpdateProductRequest(null, null, null, null, List.of("media-1"));

        assertThatThrownBy(() -> productService.updateProduct(SELLER_ID, BEARER_TOKEN, "product-1", request))
                .isInstanceOf(InvalidMediaReferenceException.class);

        verify(productRepository, never()).save(product);
        verifyNoInteractions(eventProducer);
    }

    @Test
    void rejectsAnImageAlreadyAssociatedWithAnotherProduct() {
        Product otherProduct = Product.builder()
                .id("product-2")
                .sellerId(SELLER_ID)
                .imageIds(List.of("media-1"))
                .build();
        when(productRepository.findByImageIdsContaining("media-1")).thenReturn(List.of(otherProduct));

        assertThatThrownBy(() -> productService.createProduct(
                SELLER_ID, BEARER_TOKEN, requestWith("media-1")))
                .isInstanceOf(InvalidMediaReferenceException.class)
                .hasMessageContaining("already associated");

        verify(productRepository, never()).save(org.mockito.ArgumentMatchers.any(Product.class));
        verifyNoInteractions(eventProducer);
    }

    @Test
    void retainsAnImageAlreadyAssociatedWithTheProductBeingUpdated() {
        Product product = Product.builder()
                .id("product-1")
                .sellerId(SELLER_ID)
                .name("Name")
                .description("Description")
                .price(new BigDecimal("10.00"))
                .stock(1)
                .imageIds(new java.util.ArrayList<>(List.of("media-1")))
                .build();
        when(productRepository.findByIdAndSellerId("product-1", SELLER_ID)).thenReturn(Optional.of(product));
        lenient().when(productRepository.findByImageIdsContaining("media-1")).thenReturn(List.of(product));
        when(productRepository.save(product)).thenReturn(product);

        ProductResponse response = productService.updateProduct(
                SELLER_ID,
                BEARER_TOKEN,
                "product-1",
                new UpdateProductRequest(null, null, null, null, List.of("media-1")));

        assertThat(response.imageIds()).containsExactly("media-1");
        verify(eventProducer).publishProductUpdated(product);
    }

    @Test
    void mapsAConcurrentUniqueIndexConflictToAnAssociationError() {
        when(productRepository.findByImageIdsContaining("media-1")).thenReturn(List.of());
        when(productRepository.save(org.mockito.ArgumentMatchers.any(Product.class)))
                .thenThrow(new DuplicateKeyException("duplicate imageIds index key"));

        assertThatThrownBy(() -> productService.createProduct(
                SELLER_ID, BEARER_TOKEN, requestWith("media-1")))
                .isInstanceOf(InvalidMediaReferenceException.class)
                .hasMessageContaining("already associated");

        verifyNoInteractions(eventProducer);
    }

    private CreateProductRequest requestWith(String... imageIds) {
        return new CreateProductRequest("Name", "Description", new BigDecimal("10.00"), 1, List.of(imageIds));
    }

    private Product product(String id, String sellerId, String name) {
        return Product.builder()
                .id(id)
                .sellerId(sellerId)
                .name(name)
                .description("Description")
                .price(new BigDecimal("10.00"))
                .stock(1)
                .imageIds(List.of())
                .build();
    }
}

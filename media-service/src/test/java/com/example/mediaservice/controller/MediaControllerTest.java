package com.example.mediaservice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;

import com.example.mediaservice.config.AppProperties;
import com.example.mediaservice.dto.MediaMetadataResponse;
import com.example.mediaservice.exception.GlobalExceptionHandler;
import com.example.mediaservice.exception.MediaNotFoundException;
import com.example.mediaservice.model.MediaAsset;
import com.example.mediaservice.security.SecurityConfig;
import com.example.mediaservice.service.MediaService;

@WebMvcTest(MediaController.class)
@Import({MediaController.class, GlobalExceptionHandler.class, SecurityConfig.class,
        MediaControllerTest.TestProperties.class})
@ContextConfiguration(classes = MediaControllerTest.TestApplication.class)
class MediaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MediaService mediaService;

    @Test
    void emitsAnInlineUtf8ContentDispositionWithoutHeaderInjection() {
        MediaAsset asset = MediaAsset.builder()
                .id("media-1")
                .contentType("image/png")
                .sizeBytes(1)
                .originalFileName("résumé\r\n\".png")
                .build();
        when(mediaService.getAssetById("media-1")).thenReturn(asset);
        when(mediaService.getImageContent("media-1"))
                .thenReturn(new ByteArrayInputStream(new byte[] {1}));

        ResponseEntity<InputStreamResource> response =
                new MediaController(mediaService).getImage("media-1");

        String disposition = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertThat(disposition)
                .startsWith("inline")
                .contains("filename*=UTF-8''r%C3%A9sum%C3%A9")
                .doesNotContain("\r", "\n");
    }

    @Test
    void returnsOwnerScopedMetadataWithOnlyThePublicFields() throws Exception {
        MediaMetadataResponse metadata = new MediaMetadataResponse("media-1", "seller-1", "image/png", 42L);
        when(mediaService.getMetadata("seller-1", "media-1")).thenReturn(metadata);

        mockMvc.perform(get("/media/images/media-1/metadata")
                        .with(jwt().jwt(jwt -> jwt.subject("seller-1").claim("role", "SELLER"))
                                .authorities(new SimpleGrantedAuthority("ROLE_SELLER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("media-1"))
                .andExpect(jsonPath("$.sellerId").value("seller-1"))
                .andExpect(jsonPath("$.contentType").value("image/png"))
                .andExpect(jsonPath("$.sizeBytes").value(42))
                .andExpect(jsonPath("$.*", hasSize(4)))
                .andExpect(jsonPath("$.storageKey").doesNotExist())
                .andExpect(jsonPath("$.createdAt").doesNotExist());
    }

    @Test
    void rejectsUnauthenticatedMetadataRequests() throws Exception {
        mockMvc.perform(get("/media/images/media-1/metadata"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deniesMetadataForAnotherSellerWithForbiddenStatus() throws Exception {
        when(mediaService.getMetadata("seller-2", "media-1"))
                .thenThrow(new org.springframework.security.access.AccessDeniedException("You do not own this media asset"));

        mockMvc.perform(get("/media/images/media-1/metadata")
                        .with(jwt().jwt(jwt -> jwt.subject("seller-2").claim("role", "SELLER"))
                                .authorities(new SimpleGrantedAuthority("ROLE_SELLER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void deniesClientRoleImageUploads() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "image.png", "image/png", new byte[] {1});

        mockMvc.perform(multipart("/media/images").file(file)
                        .with(jwt().jwt(jwt -> jwt.subject("client-1").claim("role", "CLIENT"))
                                .authorities(new SimpleGrantedAuthority("ROLE_CLIENT"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void deniesClientRoleImageDeletion() throws Exception {
        mockMvc.perform(delete("/media/images/media-1")
                        .with(jwt().jwt(jwt -> jwt.subject("client-1").claim("role", "CLIENT"))
                                .authorities(new SimpleGrantedAuthority("ROLE_CLIENT"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void reportsForbiddenWhenSellerAttemptsToDeleteAnotherSellersImage() throws Exception {
        org.springframework.security.access.AccessDeniedException denial =
                new org.springframework.security.access.AccessDeniedException("You do not own this media asset");
        org.mockito.Mockito.doThrow(denial).when(mediaService).deleteImage("seller-2", "media-1");

        mockMvc.perform(delete("/media/images/media-1")
                        .with(jwt().jwt(jwt -> jwt.subject("seller-2").claim("role", "SELLER"))
                                .authorities(new SimpleGrantedAuthority("ROLE_SELLER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void returnsNotFoundWhenMetadataDoesNotExist() throws Exception {
        when(mediaService.getMetadata("seller-1", "missing"))
                .thenThrow(new MediaNotFoundException("Media not found with id: missing"));

        mockMvc.perform(get("/media/images/missing/metadata")
                        .with(jwt().jwt(jwt -> jwt.subject("seller-1").claim("role", "SELLER"))
                                .authorities(new SimpleGrantedAuthority("ROLE_SELLER"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Media not found with id: missing"));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestProperties {
        @Bean
        AppProperties appProperties() {
            return new AppProperties(
                    new AppProperties.JwtProperties(
                            "01234567890123456789012345678901", 60_000, "user-service", "buy-01-api"),
                    new AppProperties.CorsProperties(java.util.List.of("http://localhost:4200")),
                    new AppProperties.StorageProperties("test-storage", "/media/images"),
                    new AppProperties.KafkaProperties(new AppProperties.KafkaProperties.Topics(
                            "image.uploaded", "image.deleted", "product.deleted")));
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {}
}

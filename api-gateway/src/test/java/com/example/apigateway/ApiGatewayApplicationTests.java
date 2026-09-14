package com.example.apigateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "app.jwt.secret=test-secret-that-is-at-least-32-characters-long",
                "app.jwt.issuer=user-service",
                "app.jwt.audience=buy-01-api",
                "app.cors.allowed-origins=http://localhost:4200",
                "eureka.client.enabled=false"
        })
class ApiGatewayApplicationTests {

    private static final String JWT_SECRET = "test-secret-that-is-at-least-32-characters-long";

    @LocalServerPort
    private int port;

    @Autowired
    private RouteLocator routeLocator;

    private WebTestClient client() {
        return WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @Test
    void configuredRoutesUseServiceDiscovery() {
        Map<String, URI> routes = routeLocator.getRoutes()
                .collectMap(route -> route.getId(), route -> route.getUri())
                .block();

        assertThat(routes)
                .containsEntry("user-service-route", URI.create("lb://user-service"))
                .containsEntry("product-service-route", URI.create("lb://product-service"))
                .containsEntry("media-service-route", URI.create("lb://media-service"));
    }

    @Test
    void protectedRouteReturnsStructuredUnauthorizedResponse() {
        client().get().uri("/me")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().exists("X-Correlation-ID")
                .expectHeader().contentType("application/json")
                .expectBody()
                .jsonPath("$.status").isEqualTo(401)
                .jsonPath("$.error").isEqualTo("Unauthorized")
                .jsonPath("$.correlationId").isNotEmpty();
    }

    @Test
    void callerCorrelationIdIsEchoedInSecurityErrorHeaderAndBody() {
        client().get().uri("/me")
                .header("X-Correlation-ID", "gateway-client-proof")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals("X-Correlation-ID", "gateway-client-proof")
                .expectBody()
                .jsonPath("$.correlationId").isEqualTo("gateway-client-proof");
    }

    @Test
    void clientRoleCannotWriteProducts() {
        client().post().uri("/products")
                .headers(headers -> headers.setBearerAuth(tokenFor("CLIENT")))
                .exchange()
                .expectStatus().isForbidden()
                .expectHeader().exists("X-Correlation-ID")
                .expectBody()
                .jsonPath("$.status").isEqualTo(403)
                .jsonPath("$.error").isEqualTo("Forbidden");
    }

    @Test
    void rejectsTokenWithWrongIssuer() {
        client().get().uri("/me")
                .headers(headers -> headers.setBearerAuth(tokenFor("CLIENT", "other-issuer", "buy-01-api")))
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.correlationId").isNotEmpty();
    }

    @Test
    void rejectsTokenWithWrongAudience() {
        client().get().uri("/me")
                .headers(headers -> headers.setBearerAuth(tokenFor("CLIENT", "user-service", "other-audience")))
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.correlationId").isNotEmpty();
    }

    @Test
    void rejectsMalformedExpiredAndWrongSignatureTokens() {
        client().get().uri("/me")
                .headers(headers -> headers.setBearerAuth("not-a-jwt"))
                .exchange()
                .expectStatus().isUnauthorized();

        client().get().uri("/me")
                .headers(headers -> headers.setBearerAuth(tokenFor(
                        "CLIENT", "user-service", "buy-01-api", Instant.now().minusSeconds(1), JWT_SECRET)))
                .exchange()
                .expectStatus().isUnauthorized();

        client().get().uri("/me")
                .headers(headers -> headers.setBearerAuth(tokenFor(
                        "CLIENT", "user-service", "buy-01-api", Instant.now().plusSeconds(300),
                        "another-test-secret-at-least-32-characters-long")))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void sellerRolePassesGatewayAuthorization() {
        client().post().uri("/products")
                .headers(headers -> headers.setBearerAuth(tokenFor("SELLER")))
                .exchange()
                .expectStatus().is5xxServerError();
    }

    @Test
    void publicReadPassesGatewayAuthorization() {
        client().get().uri("/products")
                .exchange()
                .expectStatus().is5xxServerError()
                .expectHeader().exists("X-Correlation-ID");
    }

    @Test
    void ownProductsRequiresSellerRole() {
        client().get().uri("/products/my")
                .headers(headers -> headers.setBearerAuth(tokenFor("CLIENT")))
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void allowedCorsPreflightIsAccepted() {
        client().method(HttpMethod.OPTIONS).uri("/products")
                .header(HttpHeaders.ORIGIN, "http://localhost:4200")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        "http://localhost:4200");
    }

    @Test
    void oversizedMediaRequestReturnsStructuredPayloadTooLargeResponse() {
        client().post().uri("/media/images")
                .headers(headers -> {
                    headers.setBearerAuth(tokenFor("SELLER"));
                    headers.setContentType(MediaType.MULTIPART_FORM_DATA);
                })
                .bodyValue(new byte[4 * 1024 * 1024])
                .exchange()
                .expectStatus().isEqualTo(413)
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectHeader().exists("X-Correlation-ID")
                .expectBody()
                .jsonPath("$.status").isEqualTo(413)
                .jsonPath("$.error").isEqualTo("Payload Too Large")
                .jsonPath("$.correlationId").isNotEmpty();
    }

    private String tokenFor(String role) {
        return tokenFor(role, "user-service", "buy-01-api");
    }

    private String tokenFor(String role, String issuer, String audience) {
        return tokenFor(role, issuer, audience, Instant.now().plusSeconds(300), JWT_SECRET);
    }

    private String tokenFor(String role, String issuer, String audience, Instant expiresAt, String signingSecret) {
        try {
            Instant now = Instant.now();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject("test-user")
                    .issuer(issuer)
                    .audience(audience)
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(expiresAt))
                    .claim("role", role)
                    .build();
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(new MACSigner(signingSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            return jwt.serialize();
        } catch (Exception exception) {
            throw new IllegalStateException("Could not create test JWT", exception);
        }
    }
}

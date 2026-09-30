package com.ibatulanand.productservice.controller;

import com.ibatulanand.productservice.config.SecurityConfig;
import com.ibatulanand.productservice.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
@Import(SecurityConfig.class)
class ProductControllerSecurityTest {

    private static final String BODY = "{\"name\":\"Iphone 15\",\"description\":\"Apple Iphone 15\",\"price\":1500}";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductService productService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void createProductWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/product").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        verify(productService, never()).createProduct(any());
    }

    @Test
    void createProductWithoutAdminRoleIsForbidden() throws Exception {
        givenToken("customer-token", List.of("default-roles-spring-boot-microservices-realm", "offline_access"));

        mockMvc.perform(post("/api/product").header("Authorization", "Bearer customer-token")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
        verify(productService, never()).createProduct(any());
    }

    @Test
    void createProductWithAdminRoleIsCreated() throws Exception {
        givenToken("admin-token", List.of("product-admin"));

        mockMvc.perform(post("/api/product").header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated());
        verify(productService).createProduct(any());
    }

    @Test
    void listProductsRequiresAuthenticationOnly() throws Exception {
        givenToken("customer-token", List.of("offline_access"));

        mockMvc.perform(get("/api/product")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/product").header("Authorization", "Bearer customer-token"))
                .andExpect(status().isOk());
    }

    private void givenToken(String token, List<String> realmRoles) {
        Jwt jwt = Jwt.withTokenValue(token)
                .header("alg", "RS256")
                .subject("user")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .claim("realm_access", Map.of("roles", realmRoles))
                .build();
        when(jwtDecoder.decode(token)).thenReturn(jwt);
    }
}

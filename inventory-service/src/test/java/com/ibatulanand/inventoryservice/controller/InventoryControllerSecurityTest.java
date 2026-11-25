package com.ibatulanand.inventoryservice.controller;

import com.ibatulanand.inventoryservice.config.SecurityConfig;
import com.ibatulanand.inventoryservice.dto.InventoryResponse;
import com.ibatulanand.inventoryservice.repository.InventoryRepository;
import com.ibatulanand.inventoryservice.service.InventoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InventoryController.class)
@Import(SecurityConfig.class)
class InventoryControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InventoryService inventoryService;

    @MockBean
    private InventoryRepository inventoryRepository;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void inventoryLookupWithoutTokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/inventory").param("skuCode", "iphone_15"))
                .andExpect(status().isUnauthorized());

        verify(inventoryService, never()).isInStock(anyList());
    }

    @Test
    void inventoryLookupWithInvalidTokenIsRejected() throws Exception {
        when(jwtDecoder.decode("forged")).thenThrow(new BadJwtException("bad signature"));

        mockMvc.perform(get("/api/inventory").param("skuCode", "iphone_15")
                        .header("Authorization", "Bearer forged"))
                .andExpect(status().isUnauthorized());

        verify(inventoryService, never()).isInStock(anyList());
    }

    @Test
    void inventoryLookupWithValidTokenIsAllowed() throws Exception {
        when(inventoryService.isInStock(List.of("iphone_15")))
                .thenReturn(List.of(new InventoryResponse("iphone_15", true)));

        mockMvc.perform(get("/api/inventory").param("skuCode", "iphone_15").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].skuCode").value("iphone_15"));
    }

    @Test
    void nonPublicActuatorEndpointsRequireToken() throws Exception {
        for (String path : new String[]{"/actuator/heapdump", "/actuator/env", "/actuator"}) {
            mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
    }
}

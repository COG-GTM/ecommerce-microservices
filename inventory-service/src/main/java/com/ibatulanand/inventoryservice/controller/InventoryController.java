package com.ibatulanand.inventoryservice.controller;

import com.ibatulanand.inventoryservice.dto.InventoryResponse;
import com.ibatulanand.inventoryservice.service.InventoryService;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriInfo;

import java.util.Arrays;
import java.util.List;

@Path("/api/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<InventoryResponse> isInStock(
            @QueryParam("skuCode") List<String> skuCode,
            @Context UriInfo uriInfo) {
        if (!uriInfo.getQueryParameters().containsKey("skuCode")) {
            throw new BadRequestException();
        }

        List<String> expandedSkuCodes = skuCode == null
                ? List.of()
                : skuCode.stream()
                        .flatMap(value -> Arrays.stream(value.split(",", -1)))
                        .filter(value -> !value.isEmpty())
                        .toList();
        return inventoryService.isInStock(expandedSkuCodes);
    }
}

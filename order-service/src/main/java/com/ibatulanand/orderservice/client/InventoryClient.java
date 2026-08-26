package com.ibatulanand.orderservice.client;

import com.ibatulanand.orderservice.dto.InventoryResponse;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.List;

@Path("/api/inventory")
@RegisterRestClient(configKey = "inventory")
public interface InventoryClient {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    List<InventoryResponse> isInStock(@QueryParam("skuCode") List<String> skuCodes);
}

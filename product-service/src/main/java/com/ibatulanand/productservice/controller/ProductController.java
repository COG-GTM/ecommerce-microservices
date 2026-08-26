package com.ibatulanand.productservice.controller;

import com.ibatulanand.productservice.dto.ProductRequest;
import com.ibatulanand.productservice.dto.ProductResponse;
import com.ibatulanand.productservice.service.ProductService;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

@Path("/api/product")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response createProduct(ProductRequest productRequest) {
        productService.createProduct(productRequest);
        return Response.status(Response.Status.CREATED).build();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<ProductResponse> getAllProducts() {
        return productService.getAllProducts();
    }
}

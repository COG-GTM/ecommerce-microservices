package com.ibatulanand.productservice.controller;

import com.ibatulanand.productservice.dto.ProductRequest;
import com.ibatulanand.productservice.dto.ProductResponse;
import com.ibatulanand.productservice.service.ProductService;
import com.ibatulanand.productservice.service.ProductService.DuplicateSkuException;
import com.ibatulanand.productservice.service.ProductService.DuplicateStyleException;
import com.ibatulanand.productservice.service.ProductService.ProductValidationException;
import com.ibatulanand.productservice.service.ProductService.SkuNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/product")
@RequiredArgsConstructor
public class ProductController {

    static final String PROBLEM_TYPE_BASE = "/problems/";

    private final ProductService productService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void createProduct(@RequestBody ProductRequest productRequest) {
        productService.createProduct(productRequest);
    }

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public List<ProductResponse> getAllProducts() {
        return productService.getAllProducts();
    }

    @GetMapping(params = "skuCode")
    @ResponseStatus(HttpStatus.OK)
    public ProductResponse getProductBySku(@RequestParam String skuCode) {
        return productService.getProductBySku(skuCode);
    }

    @ExceptionHandler(SkuNotFoundException.class)
    public ProblemDetail handleSkuNotFound(SkuNotFoundException e) {
        ProblemDetail problem = problem(HttpStatus.NOT_FOUND, "sku-not-found", "SKU not found", e.getMessage());
        problem.setProperty("skuCode", e.getSkuCode());
        return problem;
    }

    @ExceptionHandler(ProductValidationException.class)
    public ProblemDetail handleValidation(ProductValidationException e) {
        ProblemDetail problem = problem(HttpStatus.UNPROCESSABLE_ENTITY, "validation-failed",
                "Validation failed", e.getMessage());
        problem.setProperty("errors", e.getErrors());
        return problem;
    }

    @ExceptionHandler(DuplicateSkuException.class)
    public ProblemDetail handleDuplicateSku(DuplicateSkuException e) {
        ProblemDetail problem = problem(HttpStatus.CONFLICT, "duplicate-sku", "Duplicate SKU", e.getMessage());
        problem.setProperty("skuCodes", e.getSkuCodes());
        return problem;
    }

    @ExceptionHandler(DuplicateStyleException.class)
    public ProblemDetail handleDuplicateStyle(DuplicateStyleException e) {
        ProblemDetail problem = problem(HttpStatus.CONFLICT, "duplicate-style", "Duplicate style", e.getMessage());
        problem.setProperty("styleId", e.getStyleId());
        return problem;
    }

    private static ProblemDetail problem(HttpStatus status, String slug, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(PROBLEM_TYPE_BASE + slug));
        problem.setTitle(title);
        return problem;
    }
}

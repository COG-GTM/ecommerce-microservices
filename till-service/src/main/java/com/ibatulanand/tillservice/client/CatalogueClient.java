package com.ibatulanand.tillservice.client;

import com.ibatulanand.tillservice.domain.CatalogueItem;

import java.util.List;
import java.util.Optional;

/** Catalogue lookups (product-service). */
public interface CatalogueClient {

    Optional<CatalogueItem> findByBarcode(String barcode);

    List<CatalogueItem> quickKeys();
}

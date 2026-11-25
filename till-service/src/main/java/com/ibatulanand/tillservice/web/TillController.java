package com.ibatulanand.tillservice.web;

import com.ibatulanand.tillservice.domain.Basket;
import com.ibatulanand.tillservice.domain.CatalogueItem;
import com.ibatulanand.tillservice.service.TillService;
import com.ibatulanand.tillservice.web.TillDtos.AddLineRequest;
import com.ibatulanand.tillservice.web.TillDtos.TenderRequest;
import com.ibatulanand.tillservice.web.TillDtos.TenderResult;
import com.ibatulanand.tillservice.web.TillDtos.TillSession;
import com.ibatulanand.tillservice.web.TillDtos.UpdateLineRequest;
import com.ibatulanand.tillservice.web.TillDtos.VoidSaleRequest;
import jakarta.validation.Valid;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/till")
public class TillController {

    static final List<String> BACKING_SERVICES =
            List.of("product-service", "inventory-service", "order-service", "notification-service");

    private final TillService tillService;
    private final DiscoveryClient discoveryClient;

    public TillController(TillService tillService, DiscoveryClient discoveryClient) {
        this.tillService = tillService;
        this.discoveryClient = discoveryClient;
    }

    @GetMapping("/session")
    public TillSession session(@RequestParam(required = false) String tillId) {
        Map<String, String> services = new LinkedHashMap<>();
        for (String service : BACKING_SERVICES) {
            services.put(service, discoveryClient.getInstances(service).isEmpty() ? "DOWN" : "UP");
        }
        return tillService.session(tillId, services);
    }

    @GetMapping("/products")
    public List<CatalogueItem> products(@RequestParam(name = "quickkey", defaultValue = "true") boolean quickKey) {
        return tillService.quickKeys();
    }

    @GetMapping("/products/{barcode}")
    public CatalogueItem product(@PathVariable String barcode) {
        return tillService.product(barcode);
    }

    @GetMapping("/basket/{basketId}")
    public Basket basket(@PathVariable String basketId) {
        return tillService.basket(basketId);
    }

    @PostMapping("/basket/lines")
    @ResponseStatus(HttpStatus.CREATED)
    public Basket addLine(@Valid @RequestBody AddLineRequest request) {
        return tillService.addLine(request.basketId(), request.barcode(),
                request.quantity() == null ? 1 : request.quantity(), request.weightGrams());
    }

    @PatchMapping("/basket/lines/{lineId}")
    public Basket updateLine(@PathVariable String lineId, @Valid @RequestBody UpdateLineRequest request) {
        return tillService.updateLine(request.basketId(), lineId, request.quantity());
    }

    @DeleteMapping("/basket/lines/{lineId}")
    public Basket voidLine(@PathVariable String lineId, @RequestParam String basketId) {
        return tillService.voidLine(basketId, lineId);
    }

    @PostMapping("/basket/void")
    public Basket voidSale(@Valid @RequestBody VoidSaleRequest request) {
        return tillService.voidSale(request.basketId());
    }

    @PostMapping("/basket/tender")
    public TenderResult tender(@Valid @RequestBody TenderRequest request) {
        return tillService.tender(request);
    }
}

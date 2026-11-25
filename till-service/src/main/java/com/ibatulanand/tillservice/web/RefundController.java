package com.ibatulanand.tillservice.web;

import com.ibatulanand.tillservice.service.RefundService;
import com.ibatulanand.tillservice.web.RefundDtos.RefundRequest;
import com.ibatulanand.tillservice.web.RefundDtos.RefundResult;
import com.ibatulanand.tillservice.web.RefundDtos.RefundableSale;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/till/refund")
@RequiredArgsConstructor
public class RefundController {

    private final RefundService refunds;

    /** {reference}: receipt number (R-...), transaction number or order number of a completed sale. */
    @GetMapping("/{reference}")
    public RefundableSale lookup(@PathVariable String reference) {
        return refunds.lookup(reference);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RefundResult refund(@Valid @RequestBody RefundRequest request) {
        return refunds.refund(request);
    }
}

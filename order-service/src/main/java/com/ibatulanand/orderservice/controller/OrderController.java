package com.ibatulanand.orderservice.controller;

import com.ibatulanand.orderservice.dto.OrderRequest;
import com.ibatulanand.orderservice.service.OrderService;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.time.temporal.ChronoUnit;

@Path("/api/order")
public class OrderController {
    private static final Logger LOG = Logger.getLogger(OrderController.class);

    private static final String SUCCESS_BODY = "Order Placed Successfully!";
    private static final String FALLBACK_BODY =
            "Oops! Something went wrong, please order after some time!";

    @Inject
    OrderService orderService;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @CircuitBreaker(requestVolumeThreshold = 5, failureRatio = 0.5,
            delay = 5, delayUnit = ChronoUnit.SECONDS, successThreshold = 3)
    @Timeout(value = 3000)
    @Retry(maxRetries = 2, delay = 5, delayUnit = ChronoUnit.MILLIS)
    @Fallback(fallbackMethod = "fallbackMethod")
    public Response placeOrder(OrderRequest orderRequest) {
        orderService.placeOrder(orderRequest);
        LOG.info("Order placed successfully");
        return response(SUCCESS_BODY);
    }

    public Response fallbackMethod(OrderRequest orderRequest, Throwable failure) {
        return response(FALLBACK_BODY);
    }

    private Response response(String body) {
        return Response.status(Response.Status.CREATED)
                .type("text/plain;charset=UTF-8")
                .entity(body)
                .build();
    }
}

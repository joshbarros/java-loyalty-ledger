package com.loyalty.fulfillment;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class FulfillmentController {

    private final FulfillmentRepository fulfillments;

    public FulfillmentController(FulfillmentRepository fulfillments) {
        this.fulfillments = fulfillments;
    }

    @GetMapping("/fulfillments/{commandId}")
    public FulfillmentEntity fulfillment(@PathVariable String commandId) {
        return fulfillments.findById(commandId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "fulfillment not found"));
    }
}

package com.loyalty.fulfillment;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FulfillmentRepository extends JpaRepository<FulfillmentEntity, String> {
}

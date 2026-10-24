package com.loyalty.ledger;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AppliedCommandRepository extends JpaRepository<AppliedCommand, String> {
}

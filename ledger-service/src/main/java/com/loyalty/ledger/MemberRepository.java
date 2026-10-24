package com.loyalty.ledger;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberRepository extends JpaRepository<Member, String> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE members SET balance = balance + :points WHERE id = :id", nativeQuery = true)
    int credit(@Param("id") String id, @Param("points") int points);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE members SET balance = balance - :points WHERE id = :id AND balance >= :points", nativeQuery = true)
    int debit(@Param("id") String id, @Param("points") int points);
}

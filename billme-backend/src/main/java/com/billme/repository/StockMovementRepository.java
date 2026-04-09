package com.billme.repository;

import com.billme.product.StockMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {
    Optional<StockMovement> findByReferenceIdAndMovementType(String referenceId, StockMovement.MovementType movementType);
}

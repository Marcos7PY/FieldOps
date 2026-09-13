package com.fieldops.orders.infrastructure.persistence;

import com.fieldops.orders.domain.model.Client;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ClientRepository extends JpaRepository<Client, Long> {

    Optional<Client> findByTaxId(String taxId);

    Page<Client> findByActiveTrue(Pageable pageable);
}
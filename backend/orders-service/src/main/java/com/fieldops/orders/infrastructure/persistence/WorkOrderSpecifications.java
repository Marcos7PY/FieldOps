package com.fieldops.orders.infrastructure.persistence;

import com.fieldops.orders.domain.model.OrderStatus;
import com.fieldops.orders.domain.model.WorkOrder;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public final class WorkOrderSpecifications {

    private WorkOrderSpecifications() {
    }

    public static Specification<WorkOrder> withFilters(
            OrderStatus status,
            Long technicianId,
            Long clientId,
            LocalDateTime from,
            LocalDateTime to,
            String search
    ) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }

            if (technicianId != null) {
                predicates.add(criteriaBuilder.equal(root.get("assignedTechnicianId"), technicianId));
            }

            if (clientId != null) {
                predicates.add(criteriaBuilder.equal(root.get("client").get("id"), clientId));
            }

            if (from != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), from));
            }

            if (to != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("createdAt"), to));
            }

            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.toLowerCase().trim() + "%";
                Predicate titleMatch = criteriaBuilder.like(criteriaBuilder.lower(root.get("title")), pattern);
                Predicate codeMatch = criteriaBuilder.like(criteriaBuilder.lower(root.get("code")), pattern);
                Predicate descMatch = criteriaBuilder.like(criteriaBuilder.lower(root.get("description")), pattern);
                predicates.add(criteriaBuilder.or(titleMatch, codeMatch, descMatch));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
package com.omnibid.repository.specification;

import com.omnibid.entity.Auction;
import com.omnibid.entity.Product;
import com.omnibid.enums.AuctionStatus;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public final class AuctionSpecification {

    private AuctionSpecification() {}

    public static Specification<Auction> filter(
            String q,
            List<Long> categoryIds,
            AuctionStatus status,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Long sellerId
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            if (sellerId != null) {
                predicates.add(cb.equal(root.get("sellerId"), sellerId));
            }

            if (minPrice != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("currentPrice"), minPrice));
            }

            if (maxPrice != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("currentPrice"), maxPrice));
            }

            if (q != null && !q.isBlank()) {
                Subquery<Long> subquery = query.subquery(Long.class);
                Root<Product> productRoot = subquery.from(Product.class);
                subquery.select(productRoot.get("id"));
                subquery.where(cb.like(cb.lower(productRoot.get("title")), "%" + q.trim().toLowerCase() + "%"));
                predicates.add(root.get("productId").in(subquery));
            }

            if (categoryIds != null && !categoryIds.isEmpty()) {
                Subquery<Long> subquery = query.subquery(Long.class);
                Root<Product> productRoot = subquery.from(Product.class);
                subquery.select(productRoot.get("id"));
                subquery.where(productRoot.get("categoryId").in(categoryIds));
                predicates.add(root.get("productId").in(subquery));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}


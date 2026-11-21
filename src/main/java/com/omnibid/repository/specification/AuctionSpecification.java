package com.omnibid.repository.specification;

import com.omnibid.entity.Auction;
import com.omnibid.entity.Product;
import com.omnibid.enums.AuctionStatus;
import com.omnibid.enums.ProductStatus;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class AuctionSpecification {

    private AuctionSpecification() {}

    public static Specification<Auction> filter(
            String q,
            Long categoryId,
            AuctionStatus status,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Long sellerId,
            Instant endBefore
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Luôn loại trừ phiên của sản phẩm HIDDEN đối với public search
            Subquery<Long> hiddenSubquery = query.subquery(Long.class);
            Root<Product> hiddenProductRoot = hiddenSubquery.from(Product.class);
            hiddenSubquery.select(hiddenProductRoot.get("id"));
            hiddenSubquery.where(cb.equal(hiddenProductRoot.get("status"), ProductStatus.HIDDEN));
            predicates.add(cb.not(root.get("productId").in(hiddenSubquery)));

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

            if (endBefore != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("endTime"), endBefore));
            }

            if (q != null && !q.isBlank()) {
                Subquery<Long> subquery = query.subquery(Long.class);
                Root<Product> productRoot = subquery.from(Product.class);
                subquery.select(productRoot.get("id"));
                subquery.where(cb.like(cb.lower(productRoot.get("title")), "%" + q.trim().toLowerCase() + "%"));
                predicates.add(root.get("productId").in(subquery));
            }

            if (categoryId != null) {
                Subquery<Long> subquery = query.subquery(Long.class);
                Root<Product> productRoot = subquery.from(Product.class);
                subquery.select(productRoot.get("id"));
                subquery.where(cb.equal(productRoot.get("categoryId"), categoryId));
                predicates.add(root.get("productId").in(subquery));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}

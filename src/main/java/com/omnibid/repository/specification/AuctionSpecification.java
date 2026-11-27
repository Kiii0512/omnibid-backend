package com.omnibid.repository.specification;

import com.omnibid.common.SearchKeywordUtils;
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

    public static Specification<Auction> publicFilter(
            String keyword,
            Long categoryId,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            AuctionStatus status,
            Instant endBefore
    ) {
        return buildFilter(keyword, categoryId, minPrice, maxPrice, status, endBefore, null, true);
    }

    public static Specification<Auction> mineFilter(
            Long sellerId,
            AuctionStatus status,
            String keyword
    ) {
        return buildFilter(keyword, null, null, null, status, null, sellerId, false);
    }

    private static Specification<Auction> buildFilter(
            String keyword,
            Long categoryId,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            AuctionStatus status,
            Instant endBefore,
            Long sellerId,
            boolean excludeHiddenProducts
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

            if (endBefore != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("endTime"), endBefore));
            }

            boolean hasKeyword = keyword != null && !keyword.isBlank();
            if (excludeHiddenProducts || hasKeyword || categoryId != null) {
                Subquery<Long> subquery = query.subquery(Long.class);
                Root<Product> productRoot = subquery.from(Product.class);
                subquery.select(productRoot.get("id"));

                List<Predicate> productPredicates = new ArrayList<>();
                if (excludeHiddenProducts) {
                    productPredicates.add(cb.notEqual(productRoot.get("status"), ProductStatus.HIDDEN));
                }
                if (hasKeyword) {
                    String escaped = SearchKeywordUtils.escapeLike(keyword);
                    if (escaped != null) {
                        productPredicates.add(cb.like(
                                cb.lower(productRoot.get("title")),
                                "%" + escaped.toLowerCase() + "%",
                                '\\'
                        ));
                    }
                }
                if (categoryId != null) {
                    productPredicates.add(cb.equal(productRoot.get("categoryId"), categoryId));
                }

                subquery.where(cb.and(productPredicates.toArray(new Predicate[0])));
                predicates.add(root.get("productId").in(subquery));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}

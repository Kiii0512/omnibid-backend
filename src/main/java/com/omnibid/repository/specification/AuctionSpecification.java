package com.omnibid.repository.specification;

import com.omnibid.dto.auction.AuctionFilterRequest;
import com.omnibid.entity.Auction;
import com.omnibid.entity.Product;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

public final class AuctionSpecification {

    private AuctionSpecification() {
    }

    public static Specification<Auction> withFilter(AuctionFilterRequest filter) {
        return (root, query, cb) -> {
            if (filter == null) {
                return cb.conjunction();
            }

            List<Predicate> predicates = new ArrayList<>();

            if (filter.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), filter.getStatus()));
            }

            if (filter.getMinPrice() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("currentPrice"), filter.getMinPrice()));
            }

            if (filter.getMaxPrice() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("currentPrice"), filter.getMaxPrice()));
            }

            if (filter.getSellerId() != null) {
                predicates.add(cb.equal(root.get("sellerId"), filter.getSellerId()));
            }

            boolean hasKeyword = StringUtils.hasText(filter.getQ());
            boolean hasCategories = filter.getCategoryIds() != null && !filter.getCategoryIds().isEmpty();

            if (hasKeyword || hasCategories) {
                Subquery<Long> productSubquery = query.subquery(Long.class);
                Root<Product> productRoot = productSubquery.from(Product.class);
                List<Predicate> productPredicates = new ArrayList<>();
                productPredicates.add(cb.equal(productRoot.get("id"), root.get("productId")));

                if (hasKeyword) {
                    String pattern = "%" + filter.getQ().trim().toLowerCase() + "%";
                    productPredicates.add(cb.like(cb.lower(productRoot.get("title")), pattern));
                }

                if (hasCategories) {
                    productPredicates.add(productRoot.get("categoryId").in(filter.getCategoryIds()));
                }

                productSubquery.select(productRoot.get("id"))
                        .where(productPredicates.toArray(new Predicate[0]));
                predicates.add(cb.exists(productSubquery));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}


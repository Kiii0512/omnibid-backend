package com.omnibid.mapper;

import com.omnibid.common.PageResponse;
import com.omnibid.dto.auction.AuctionResponse;
import com.omnibid.dto.product.ProductImageResponse;
import com.omnibid.entity.*;
import com.omnibid.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class AuctionMapper {

    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public AuctionResponse toAuctionResponse(Auction auction, Long currentUserId) {
        if (auction == null) return null;

        Product product = productRepository.findById(auction.getProductId()).orElse(null);
        Category category = (product != null) ? categoryRepository.findById(product.getCategoryId()).orElse(null) : null;
        User seller = userRepository.findById(auction.getSellerId()).orElse(null);
        User highestBidder = (auction.getHighestBidderId() != null)
                ? userRepository.findById(auction.getHighestBidderId()).orElse(null)
                : null;

        List<ProductImage> imageEntities = (product != null)
                ? productImageRepository.findByProductIdOrderByIdAsc(product.getId())
                : List.of();

        return buildResponse(auction, product, category, seller, highestBidder, imageEntities);
    }

    public PageResponse<AuctionResponse> toAuctionResponsePage(Page<Auction> page, Long currentUserId) {
        List<Auction> auctions = page.getContent();
        if (auctions.isEmpty()) {
            return PageResponse.from(page.map(a -> (AuctionResponse) null));
        }

        Set<Long> productIds = auctions.stream().map(Auction::getProductId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> userIds = new HashSet<>();
        auctions.forEach(a -> {
            if (a.getSellerId() != null) userIds.add(a.getSellerId());
            if (a.getHighestBidderId() != null) userIds.add(a.getHighestBidderId());
        });

        Map<Long, Product> productMap = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p, (a, b) -> a));

        Set<Long> categoryIds = productMap.values().stream().map(Product::getCategoryId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Category> categoryMap = categoryRepository.findAllById(categoryIds).stream()
                .collect(Collectors.toMap(Category::getId, c -> c, (a, b) -> a));

        Map<Long, User> userMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));

        Map<Long, List<ProductImage>> imagesByProduct = new HashMap<>();
        for (Long pid : productIds) {
            imagesByProduct.put(pid, productImageRepository.findByProductIdOrderByIdAsc(pid));
        }

        List<AuctionResponse> content = auctions.stream().map(auction -> {
            Product product = productMap.get(auction.getProductId());
            Category category = (product != null) ? categoryMap.get(product.getCategoryId()) : null;
            User seller = userMap.get(auction.getSellerId());
            User highestBidder = (auction.getHighestBidderId() != null) ? userMap.get(auction.getHighestBidderId()) : null;
            List<ProductImage> imageEntities = (product != null) ? imagesByProduct.getOrDefault(product.getId(), List.of()) : List.of();

            return buildResponse(auction, product, category, seller, highestBidder, imageEntities);
        }).toList();

        return PageResponse.<AuctionResponse>builder()
                .content(content)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .build();
    }

    private AuctionResponse buildResponse(
            Auction auction,
            Product product,
            Category category,
            User seller,
            User highestBidder,
            List<ProductImage> imageEntities
    ) {
        String productTitle = (product != null) ? product.getTitle() : null;
        String productDescription = (product != null) ? product.getDescription() : null;
        Long categoryId = (category != null) ? category.getId() : null;
        String categoryName = (category != null) ? category.getName() : null;
        var condition = (product != null) ? product.getCondition() : null;
        String sellerName = (seller != null) ? seller.getFullName() : null;
        String highestBidderName = (highestBidder != null) ? maskName(highestBidder.getFullName()) : null;

        List<ProductImageResponse> images = imageEntities.stream().map(this::toImageResponse).toList();
        String primaryImageUrl = imageEntities.stream()
                .filter(img -> Boolean.TRUE.equals(img.getIsPrimary()))
                .map(ProductImage::getImageUrl)
                .findFirst()
                .orElse(imageEntities.isEmpty() ? null : imageEntities.get(0).getImageUrl());

        BigDecimal minNextBid = auction.minNextBid();
        String reserveStatus = auction.reserveStatus().name();

        return AuctionResponse.builder()
                .id(auction.getId())
                .productId(auction.getProductId())
                .productTitle(productTitle)
                .productDescription(productDescription)
                .categoryId(categoryId)
                .categoryName(categoryName)
                .condition(condition)
                .sellerId(auction.getSellerId())
                .sellerName(sellerName)
                .startPrice(auction.getStartPrice())
                .bidIncrement(auction.getBidIncrement())
                .currentPrice(auction.getCurrentPrice())
                .minNextBid(minNextBid)
                .reserveStatus(reserveStatus)
                .highestBidderId(auction.getHighestBidderId())
                .highestBidderName(highestBidderName)
                .bidCount(auction.getBidCount())
                .startTime(auction.getStartTime())
                .endTime(auction.getEndTime())
                .status(auction.getStatus())
                .winnerId(auction.getWinnerId())
                .finalPrice(auction.getFinalPrice())
                .closedAt(auction.getClosedAt())
                .cancelReason(auction.getCancelReason())
                .createdAt(auction.getCreatedAt())
                .primaryImageUrl(primaryImageUrl)
                .images(images)
                .serverTime(Instant.now(clock))
                .build();
    }

    private ProductImageResponse toImageResponse(ProductImage image) {
        return new ProductImageResponse(
                image.getId(),
                image.getImageUrl(),
                image.getPublicId(),
                image.getIsPrimary(),
                image.getCreatedAt()
        );
    }

    private String maskName(String fullName) {
        if (fullName == null || fullName.isBlank()) return "***";
        String[] parts = fullName.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i];
            if (p.length() <= 2) {
                sb.append(p.charAt(0)).append("*");
            } else {
                sb.append(p.charAt(0))
                        .append("*".repeat(p.length() - 2))
                        .append(p.charAt(p.length() - 1));
            }
            if (i < parts.length - 1) sb.append(" ");
        }
        return sb.toString();
    }
}

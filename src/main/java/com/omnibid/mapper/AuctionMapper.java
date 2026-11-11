package com.omnibid.mapper;

import com.omnibid.dto.auction.AuctionDetailResponse;
import com.omnibid.dto.auction.AuctionResponse;
import com.omnibid.dto.product.ProductImageResponse;
import com.omnibid.entity.Auction;
import com.omnibid.entity.Category;
import com.omnibid.entity.Product;
import com.omnibid.entity.ProductImage;
import com.omnibid.entity.User;
import com.omnibid.enums.ProductCondition;
import com.omnibid.repository.CategoryRepository;
import com.omnibid.repository.ProductImageRepository;
import com.omnibid.repository.ProductRepository;
import com.omnibid.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class AuctionMapper {

    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;

    public AuctionResponse toAuctionResponse(Auction auction) {
        Product product = productRepository.findById(auction.getProductId()).orElse(null);
        Category category = product != null
                ? categoryRepository.findById(product.getCategoryId()).orElse(null)
                : null;
        User seller = userRepository.findById(auction.getSellerId()).orElse(null);
        User highestBidder = auction.getHighestBidderId() != null
                ? userRepository.findById(auction.getHighestBidderId()).orElse(null)
                : null;
        List<ProductImage> productImages = productImageRepository
                .findByProductIdOrderByIsPrimaryDescCreatedAtAsc(auction.getProductId());

        return buildAuctionResponse(
                auction,
                product,
                category,
                seller,
                highestBidder,
                productImages,
                LocalDateTime.now()
        );
    }

    public Page<AuctionResponse> toAuctionResponsePage(Page<Auction> auctionPage) {
        List<Auction> auctions = auctionPage.getContent();
        if (auctions.isEmpty()) {
            return auctionPage.map(this::toAuctionResponse);
        }

        Set<Long> productIds = new HashSet<>();
        Set<Long> userIds = new HashSet<>();
        for (Auction auction : auctions) {
            productIds.add(auction.getProductId());
            userIds.add(auction.getSellerId());
            if (auction.getHighestBidderId() != null) {
                userIds.add(auction.getHighestBidderId());
            }
        }

        Map<Long, Product> productMap = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity(), (a, b) -> a));

        Set<Long> categoryIds = productMap.values().stream()
                .map(Product::getCategoryId)
                .collect(Collectors.toSet());

        Map<Long, Category> categoryMap = categoryIds.isEmpty()
                ? Collections.emptyMap()
                : categoryRepository.findAllById(categoryIds).stream()
                        .collect(Collectors.toMap(Category::getId, Function.identity(), (a, b) -> a));

        Map<Long, User> userMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));

        Map<Long, List<ProductImage>> imageMap = new HashMap<>();
        List<ProductImage> allImages = productImageRepository.findByProductIdInOrderByIsPrimaryDescCreatedAtAsc(productIds);
        if (allImages != null) {
            for (ProductImage image : allImages) {
                imageMap.computeIfAbsent(image.getProductId(), k -> new ArrayList<>()).add(image);
            }
        }

        LocalDateTime serverTime = LocalDateTime.now();

        return auctionPage.map(auction -> {
            Product product = productMap.get(auction.getProductId());
            Category category = product != null ? categoryMap.get(product.getCategoryId()) : null;
            User seller = userMap.get(auction.getSellerId());
            User highestBidder = auction.getHighestBidderId() != null
                    ? userMap.get(auction.getHighestBidderId())
                    : null;
            List<ProductImage> productImages = imageMap.getOrDefault(auction.getProductId(), Collections.emptyList());

            return buildAuctionResponse(
                    auction,
                    product,
                    category,
                    seller,
                    highestBidder,
                    productImages,
                    serverTime
            );
        });
    }

    public AuctionDetailResponse toAuctionDetailResponse(Auction auction, Long currentUserId, boolean isAdmin) {
        Product product = productRepository.findById(auction.getProductId()).orElse(null);
        Category category = product != null
                ? categoryRepository.findById(product.getCategoryId()).orElse(null)
                : null;
        User seller = userRepository.findById(auction.getSellerId()).orElse(null);
        User highestBidder = auction.getHighestBidderId() != null
                ? userRepository.findById(auction.getHighestBidderId()).orElse(null)
                : null;
        List<ProductImage> productImages = productImageRepository
                .findByProductIdOrderByIsPrimaryDescCreatedAtAsc(auction.getProductId());

        List<ProductImageResponse> imageResponses = productImages == null
                ? Collections.emptyList()
                : productImages.stream().map(this::toImageResponse).toList();

        boolean canViewReservePrice = isAdmin
                || (currentUserId != null && currentUserId.equals(auction.getSellerId()));
        BigDecimal visibleReservePrice = canViewReservePrice ? auction.getReservePrice() : null;

        return AuctionDetailResponse.builder()
                .id(auction.getId())
                .productId(auction.getProductId())
                .productTitle(product != null ? product.getTitle() : null)
                .primaryImageUrl(resolvePrimaryImageUrl(productImages))
                .categoryId(product != null ? product.getCategoryId() : null)
                .categoryName(category != null ? category.getName() : null)
                .condition(product != null ? product.getCondition() : null)
                .productDescription(product != null ? product.getDescription() : null)
                .sellerId(auction.getSellerId())
                .sellerName(seller != null ? seller.getFullName() : null)
                .startPrice(auction.getStartPrice())
                .bidIncrement(auction.getBidIncrement())
                .currentPrice(auction.getCurrentPrice())
                .minNextBid(calculateMinNextBid(auction))
                .reserveReached(isReserveReached(auction))
                .reservePrice(visibleReservePrice)
                .highestBidderId(auction.getHighestBidderId())
                .highestBidderName(highestBidder != null ? highestBidder.getFullName() : null)
                .bidCount(auction.getBidCount())
                .startTime(auction.getStartTime())
                .endTime(auction.getEndTime())
                .serverTime(LocalDateTime.now())
                .status(auction.getStatus())
                .winnerId(auction.getWinnerId())
                .finalPrice(auction.getFinalPrice())
                .images(imageResponses)
                .build();
    }

    public BigDecimal calculateMinNextBid(Auction auction) {
        if (auction.getCurrentPrice() == null || auction.getBidIncrement() == null) {
            return null;
        }
        return auction.getCurrentPrice().add(auction.getBidIncrement());
    }

    public boolean isReserveReached(Auction auction) {
        if (auction.getReservePrice() == null) {
            return true;
        }
        return auction.getCurrentPrice() != null
                && auction.getCurrentPrice().compareTo(auction.getReservePrice()) >= 0;
    }

    public String resolvePrimaryImageUrl(List<ProductImage> images) {
        if (images == null || images.isEmpty()) {
            return null;
        }
        return images.get(0).getImageUrl();
    }

    public ProductImageResponse toImageResponse(ProductImage image) {
        return ProductImageResponse.builder()
                .id(image.getId())
                .imageUrl(image.getImageUrl())
                .publicId(image.getPublicId())
                .isPrimary(image.getIsPrimary())
                .createdAt(image.getCreatedAt())
                .build();
    }

    private AuctionResponse buildAuctionResponse(
            Auction auction,
            Product product,
            Category category,
            User seller,
            User highestBidder,
            List<ProductImage> productImages,
            LocalDateTime serverTime
    ) {
        String productTitle = product != null ? product.getTitle() : null;
        Long categoryId = product != null ? product.getCategoryId() : null;
        ProductCondition condition = product != null ? product.getCondition() : null;
        String categoryName = category != null ? category.getName() : null;
        String sellerName = seller != null ? seller.getFullName() : null;
        String highestBidderName = highestBidder != null ? highestBidder.getFullName() : null;

        return AuctionResponse.builder()
                .id(auction.getId())
                .productId(auction.getProductId())
                .productTitle(productTitle)
                .primaryImageUrl(resolvePrimaryImageUrl(productImages))
                .categoryId(categoryId)
                .categoryName(categoryName)
                .condition(condition)
                .sellerId(auction.getSellerId())
                .sellerName(sellerName)
                .startPrice(auction.getStartPrice())
                .bidIncrement(auction.getBidIncrement())
                .currentPrice(auction.getCurrentPrice())
                .minNextBid(calculateMinNextBid(auction))
                .reserveReached(isReserveReached(auction))
                .highestBidderId(auction.getHighestBidderId())
                .highestBidderName(highestBidderName)
                .bidCount(auction.getBidCount())
                .startTime(auction.getStartTime())
                .endTime(auction.getEndTime())
                .serverTime(serverTime)
                .status(auction.getStatus())
                .winnerId(auction.getWinnerId())
                .finalPrice(auction.getFinalPrice())
                .build();
    }
}


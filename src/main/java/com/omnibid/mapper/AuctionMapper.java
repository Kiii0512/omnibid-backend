package com.omnibid.mapper;

import com.omnibid.common.MaskingUtils;
import com.omnibid.common.PageResponse;
import com.omnibid.dto.auction.AuctionDetailResponse;
import com.omnibid.dto.auction.AuctionSummaryResponse;
import com.omnibid.dto.auction.MyAuctionItemResponse;
import com.omnibid.dto.product.ProductImageResponse;
import com.omnibid.dto.user.UserRef;
import com.omnibid.entity.Auction;
import com.omnibid.entity.Category;
import com.omnibid.entity.Order;
import com.omnibid.entity.Product;
import com.omnibid.entity.ProductImage;
import com.omnibid.entity.User;
import com.omnibid.enums.AuctionStatus;
import com.omnibid.repository.CategoryRepository;
import com.omnibid.repository.OrderRepository;
import com.omnibid.repository.ProductImageRepository;
import com.omnibid.repository.ProductRepository;
import com.omnibid.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class AuctionMapper {

    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;

    public AuctionDetailResponse toDetail(Auction auction, Long currentUserId) {
        if (auction == null) {
            return null;
        }
        Product product = productRepository.findById(auction.getProductId()).orElse(null);
        return toDetail(auction, product, currentUserId);
    }

    public AuctionDetailResponse toDetail(Auction auction, Product product, Long currentUserId) {
        if (auction == null) {
            return null;
        }

        Category category = (product != null && product.getCategoryId() != null)
                ? categoryRepository.findById(product.getCategoryId()).orElse(null)
                : null;

        Set<Long> userIds = new HashSet<>();
        if (auction.getSellerId() != null) userIds.add(auction.getSellerId());
        if (auction.getHighestBidderId() != null) userIds.add(auction.getHighestBidderId());
        if (auction.getWinnerId() != null) userIds.add(auction.getWinnerId());

        Map<Long, User> users = userIds.isEmpty()
                ? Map.of()
                : userRepository.findAllById(userIds).stream()
                        .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));

        User seller = users.get(auction.getSellerId());
        User highestBidder = auction.getHighestBidderId() != null ? users.get(auction.getHighestBidderId()) : null;
        User winner = auction.getWinnerId() != null ? users.get(auction.getWinnerId()) : null;

        List<ProductImage> imageEntities = (product != null)
                ? productImageRepository.findByProductIdOrderByIdAsc(product.getId())
                : List.of();
        List<ProductImageResponse> images = imageEntities.stream()
                .map(img -> new ProductImageResponse(img.getId(), img.getImageUrl(), Boolean.TRUE.equals(img.getIsPrimary())))
                .toList();
        String primaryImageUrl = resolvePrimaryImageUrl(imageEntities);

        boolean viewerIsSeller = currentUserId != null && currentUserId.equals(auction.getSellerId());
        boolean viewerIsLeading = currentUserId != null && currentUserId.equals(auction.getHighestBidderId());
        boolean viewerIsWinner = currentUserId != null && currentUserId.equals(auction.getWinnerId());

        Long orderId = null;
        if (auction.getStatus() == AuctionStatus.ENDED && (viewerIsSeller || viewerIsWinner)) {
            orderId = orderRepository.findByAuctionId(auction.getId())
                    .map(Order::getId)
                    .orElse(null);
        }

        return new AuctionDetailResponse(
                auction.getId(),
                auction.getProductId(),
                product != null ? product.getTitle() : null,
                primaryImageUrl,
                product != null ? product.getCategoryId() : null,
                category != null ? category.getName() : null,
                auction.getStatus(),
                auction.getStartPrice(),
                auction.getCurrentPrice(),
                auction.minNextBid(),
                auction.getBidCount() != null ? auction.getBidCount() : 0,
                auction.reserveStatus(),
                auction.getStartTime(),
                auction.getEndTime(),
                auction.getCreatedAt(),
                product != null ? product.getDescription() : null,
                product != null ? product.getCondition() : null,
                images,
                auction.getBidIncrement(),
                new UserRef(auction.getSellerId(), seller != null ? seller.getFullName() : null),
                highestBidder != null ? MaskingUtils.maskName(highestBidder.getFullName()) : null,
                viewerIsSeller,
                viewerIsLeading,
                viewerIsSeller ? auction.getReservePrice() : null,
                winner != null ? MaskingUtils.maskName(winner.getFullName()) : null,
                auction.getFinalPrice(),
                auction.getClosedAt(),
                auction.getCancelReason(),
                orderId
        );
    }

    public PageResponse<AuctionSummaryResponse> toSummaryPage(Page<Auction> page) {
        List<Auction> auctions = page.getContent();
        if (auctions.isEmpty()) {
            return emptyPage(page);
        }

        BatchContext ctx = loadBatchContext(auctions, false);

        List<AuctionSummaryResponse> content = auctions.stream().map(auction -> {
            Product product = ctx.products().get(auction.getProductId());
            Category category = (product != null) ? ctx.categories().get(product.getCategoryId()) : null;
            String primaryImageUrl = (product != null) ? ctx.primaryImageUrls().get(product.getId()) : null;

            return new AuctionSummaryResponse(
                    auction.getId(),
                    auction.getProductId(),
                    product != null ? product.getTitle() : null,
                    primaryImageUrl,
                    product != null ? product.getCategoryId() : null,
                    category != null ? category.getName() : null,
                    auction.getStatus(),
                    auction.getStartPrice(),
                    auction.getCurrentPrice(),
                    auction.minNextBid(),
                    auction.getBidCount() != null ? auction.getBidCount() : 0,
                    auction.reserveStatus(),
                    auction.getStartTime(),
                    auction.getEndTime(),
                    auction.getCreatedAt()
            );
        }).toList();

        return buildPage(page, content);
    }

    public PageResponse<MyAuctionItemResponse> toMyAuctionPage(Page<Auction> page) {
        List<Auction> auctions = page.getContent();
        if (auctions.isEmpty()) {
            return emptyPage(page);
        }

        BatchContext ctx = loadBatchContext(auctions, true);

        List<MyAuctionItemResponse> content = auctions.stream().map(auction -> {
            Product product = ctx.products().get(auction.getProductId());
            Category category = (product != null) ? ctx.categories().get(product.getCategoryId()) : null;
            String primaryImageUrl = (product != null) ? ctx.primaryImageUrls().get(product.getId()) : null;
            Long orderId = (auction.getStatus() == AuctionStatus.ENDED)
                    ? ctx.orderIdsByAuction().get(auction.getId())
                    : null;

            return new MyAuctionItemResponse(
                    auction.getId(),
                    auction.getProductId(),
                    product != null ? product.getTitle() : null,
                    primaryImageUrl,
                    product != null ? product.getCategoryId() : null,
                    category != null ? category.getName() : null,
                    auction.getStatus(),
                    auction.getStartPrice(),
                    auction.getCurrentPrice(),
                    auction.minNextBid(),
                    auction.getBidCount() != null ? auction.getBidCount() : 0,
                    auction.reserveStatus(),
                    auction.getStartTime(),
                    auction.getEndTime(),
                    auction.getCreatedAt(),
                    auction.getReservePrice(),
                    orderId
            );
        }).toList();

        return buildPage(page, content);
    }

    private BatchContext loadBatchContext(List<Auction> auctions, boolean loadOrders) {
        Set<Long> productIds = auctions.stream()
                .map(Auction::getProductId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, Product> productMap = productIds.isEmpty()
                ? Map.of()
                : productRepository.findAllById(productIds).stream()
                        .collect(Collectors.toMap(Product::getId, p -> p, (a, b) -> a));

        Set<Long> categoryIds = productMap.values().stream()
                .map(Product::getCategoryId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, Category> categoryMap = categoryIds.isEmpty()
                ? Map.of()
                : categoryRepository.findAllById(categoryIds).stream()
                        .collect(Collectors.toMap(Category::getId, c -> c, (a, b) -> a));

        Map<Long, String> primaryImageUrls = new HashMap<>();
        if (!productIds.isEmpty()) {
            Map<Long, List<ProductImage>> grouped = productImageRepository
                    .findByProductIdInOrderByIdAsc(productIds).stream()
                    .collect(Collectors.groupingBy(ProductImage::getProductId));
            grouped.forEach((pid, imgs) -> primaryImageUrls.put(pid, resolvePrimaryImageUrl(imgs)));
        }

        Map<Long, Long> orderIdsByAuction = Collections.emptyMap();
        if (loadOrders) {
            List<Long> endedAuctionIds = auctions.stream()
                    .filter(a -> a.getStatus() == AuctionStatus.ENDED)
                    .map(Auction::getId)
                    .toList();
            if (!endedAuctionIds.isEmpty()) {
                orderIdsByAuction = orderRepository.findByAuctionIdIn(endedAuctionIds).stream()
                        .collect(Collectors.toMap(Order::getAuctionId, Order::getId, (a, b) -> a));
            }
        }

        return new BatchContext(productMap, categoryMap, primaryImageUrls, orderIdsByAuction);
    }

    private String resolvePrimaryImageUrl(List<ProductImage> images) {
        if (images == null || images.isEmpty()) {
            return null;
        }
        return images.stream()
                .filter(img -> Boolean.TRUE.equals(img.getIsPrimary()))
                .map(ProductImage::getImageUrl)
                .findFirst()
                .orElse(images.get(0).getImageUrl());
    }

    private <T> PageResponse<T> emptyPage(Page<Auction> page) {
        return PageResponse.<T>builder()
                .content(List.of())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .build();
    }

    private <T> PageResponse<T> buildPage(Page<Auction> page, List<T> content) {
        return PageResponse.<T>builder()
                .content(content)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .build();
    }

    private record BatchContext(
            Map<Long, Product> products,
            Map<Long, Category> categories,
            Map<Long, String> primaryImageUrls,
            Map<Long, Long> orderIdsByAuction
    ) {}
}

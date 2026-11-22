package com.omnibid.service.impl;

import com.omnibid.common.BusinessException;
import com.omnibid.common.ErrorCode;
import com.omnibid.common.MaskingUtils;
import com.omnibid.common.NotificationTemplates;
import com.omnibid.common.PageResponse;
import com.omnibid.common.SortWhitelist;
import com.omnibid.dto.bid.BidHistoryItem;
import com.omnibid.dto.bid.BidRequest;
import com.omnibid.dto.bid.MyBidItem;
import com.omnibid.dto.bid.PlaceBidResponse;
import com.omnibid.dto.websocket.AuctionEvent;
import com.omnibid.entity.Auction;
import com.omnibid.entity.Bid;
import com.omnibid.entity.Order;
import com.omnibid.entity.Product;
import com.omnibid.entity.ProductImage;
import com.omnibid.entity.User;
import com.omnibid.enums.AuctionEventType;
import com.omnibid.enums.AuctionStatus;
import com.omnibid.enums.NotificationType;
import com.omnibid.enums.ProductStatus;
import com.omnibid.repository.AuctionRepository;
import com.omnibid.repository.BidRepository;
import com.omnibid.repository.OrderRepository;
import com.omnibid.repository.ProductImageRepository;
import com.omnibid.repository.ProductRepository;
import com.omnibid.repository.UserRepository;
import com.omnibid.security.CurrentUser;
import com.omnibid.service.BidService;
import com.omnibid.service.NotificationService;
import com.omnibid.service.WebSocketPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BidServiceImpl implements BidService {

    private static final Set<String> BID_SORT_FIELDS = Set.of("createdAt", "amount");

    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final WebSocketPublisher webSocketPublisher;
    private final NotificationService notificationService;
    private final Clock clock;

    @Override
    @Transactional
    public PlaceBidResponse placeBid(Long auctionId, Long bidderId, String bidderFullName, BidRequest request) {
        // Không truy vấn và không khóa bảng users (trạng thái BANNED đã được JWT Filter chặn 403)
        Auction auction = auctionRepository.findByIdForUpdate(auctionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy phiên đấu giá"));

        Product product = productRepository.findById(auction.getProductId()).orElse(null);
        if (product == null || product.getStatus() == ProductStatus.HIDDEN) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy phiên đấu giá");
        }

        Long previousLeaderId = auction.getHighestBidderId();
        Instant now = Instant.now(clock);
        Bid newBid = auction.placeBid(bidderId, request.amount(), now);

        Bid savedBid;
        try {
            savedBid = bidRepository.save(newBid);
            bidRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            log.warn("Duplicate bid constraint triggered: auctionId={}, amount={}", auction.getId(), request.amount());
            throw new BusinessException(ErrorCode.CONFLICT, "Mức giá đã có người đặt, vui lòng thử lại");
        }

        auctionRepository.save(auction);

        if (previousLeaderId != null && !previousLeaderId.equals(bidderId)) {
            notificationService.notify(
                    previousLeaderId,
                    NotificationType.OUTBID,
                    NotificationTemplates.outbidTitle(),
                    NotificationTemplates.outbidContent(auction.getId(), auction.getCurrentPrice()),
                    "AUCTION",
                    auction.getId()
            );
        }

        String maskedBidderName = MaskingUtils.maskName(bidderFullName);
        Instant bidCreatedAt = savedBid.getCreatedAt() != null ? savedBid.getCreatedAt() : now;
        BidHistoryItem bidItem = new BidHistoryItem(
                savedBid.getId(),
                savedBid.getAmount(),
                maskedBidderName,
                bidCreatedAt
        );

        webSocketPublisher.publishAuctionEvent(
                AuctionEvent.of(AuctionEventType.BID_PLACED, auction, maskedBidderName, bidItem, now)
        );

        log.info("Bid placed successfully: bidId={}, auctionId={}, bidderId={}, amount={}",
                savedBid.getId(), auction.getId(), bidderId, savedBid.getAmount());

        return new PlaceBidResponse(
                savedBid.getId(),
                auction.getId(),
                savedBid.getAmount(),
                auction.getCurrentPrice(),
                auction.getBidCount(),
                auction.minNextBid()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<BidHistoryItem> getBidsByAuction(Long auctionId, CurrentUser currentUser, Pageable pageable) {
        SortWhitelist.validate(pageable, BID_SORT_FIELDS);

        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy phiên đấu giá"));

        boolean isAdmin = currentUser != null && currentUser.isAdmin();
        if (!isAdmin) {
            Product product = productRepository.findById(auction.getProductId()).orElse(null);
            if (product == null || product.getStatus() == ProductStatus.HIDDEN) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy phiên đấu giá");
            }
        }

        Page<Bid> page = bidRepository.findByAuctionId(auctionId, pageable);
        List<Bid> bids = page.getContent();
        if (bids.isEmpty()) {
            return PageResponse.<BidHistoryItem>builder()
                    .content(List.of())
                    .page(page.getNumber())
                    .size(page.getSize())
                    .totalElements(page.getTotalElements())
                    .totalPages(page.getTotalPages())
                    .build();
        }

        Set<Long> bidderIds = bids.stream()
                .map(Bid::getBidderId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> maskedNames = userRepository.findAllById(bidderIds).stream()
                .collect(Collectors.toMap(User::getId, u -> MaskingUtils.maskName(u.getFullName()), (a, b) -> a));

        List<BidHistoryItem> content = bids.stream()
                .map(bid -> new BidHistoryItem(
                        bid.getId(),
                        bid.getAmount(),
                        maskedNames.getOrDefault(bid.getBidderId(), "***"),
                        bid.getCreatedAt()
                ))
                .toList();

        return PageResponse.<BidHistoryItem>builder()
                .content(content)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<MyBidItem> getMyBids(Long bidderId, Long auctionId, Pageable pageable) {
        SortWhitelist.validate(pageable, BID_SORT_FIELDS);

        Page<Bid> page = (auctionId != null)
                ? bidRepository.findByBidderIdAndAuctionId(bidderId, auctionId, pageable)
                : bidRepository.findByBidderId(bidderId, pageable);

        List<Bid> bids = page.getContent();
        if (bids.isEmpty()) {
            return PageResponse.<MyBidItem>builder()
                    .content(List.of())
                    .page(page.getNumber())
                    .size(page.getSize())
                    .totalElements(page.getTotalElements())
                    .totalPages(page.getTotalPages())
                    .build();
        }

        Set<Long> auctionIds = bids.stream()
                .map(Bid::getAuctionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Auction> auctionMap = auctionRepository.findAllById(auctionIds).stream()
                .collect(Collectors.toMap(Auction::getId, a -> a, (a, b) -> a));

        Set<Long> productIds = auctionMap.values().stream()
                .map(Auction::getProductId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Product> productMap = productIds.isEmpty()
                ? Map.of()
                : productRepository.findAllById(productIds).stream()
                        .collect(Collectors.toMap(Product::getId, p -> p, (a, b) -> a));

        Map<Long, String> primaryImageUrls = new HashMap<>();
        if (!productIds.isEmpty()) {
            Map<Long, List<ProductImage>> grouped = productImageRepository
                    .findByProductIdInOrderByIdAsc(productIds).stream()
                    .collect(Collectors.groupingBy(ProductImage::getProductId));
            grouped.forEach((pid, imgs) -> {
                String url = imgs.stream()
                        .filter(img -> Boolean.TRUE.equals(img.getIsPrimary()))
                        .map(ProductImage::getImageUrl)
                        .findFirst()
                        .orElse(imgs.isEmpty() ? null : imgs.get(0).getImageUrl());
                primaryImageUrls.put(pid, url);
            });
        }

        List<Long> wonAuctionIds = auctionMap.values().stream()
                .filter(a -> a.getStatus() == AuctionStatus.ENDED && Objects.equals(a.getWinnerId(), bidderId))
                .map(Auction::getId)
                .toList();
        Map<Long, Long> orderIdByAuction = wonAuctionIds.isEmpty()
                ? Map.of()
                : orderRepository.findByAuctionIdIn(wonAuctionIds).stream()
                        .collect(Collectors.toMap(Order::getAuctionId, Order::getId, (a, b) -> a));

        List<MyBidItem> content = bids.stream().map(bid -> {
            Auction auction = auctionMap.get(bid.getAuctionId());
            Product product = auction != null ? productMap.get(auction.getProductId()) : null;
            String primaryImageUrl = product != null ? primaryImageUrls.get(product.getId()) : null;
            boolean leading = auction != null && Objects.equals(auction.getHighestBidderId(), bidderId);
            Long orderId = (auction != null
                    && auction.getStatus() == AuctionStatus.ENDED
                    && Objects.equals(auction.getWinnerId(), bidderId))
                    ? orderIdByAuction.get(auction.getId())
                    : null;

            return new MyBidItem(
                    bid.getId(),
                    bid.getAuctionId(),
                    product != null ? product.getTitle() : null,
                    primaryImageUrl,
                    bid.getAmount(),
                    bid.getCreatedAt(),
                    auction != null ? auction.getStatus() : null,
                    auction != null ? auction.getCurrentPrice() : null,
                    auction != null ? auction.getEndTime() : null,
                    leading,
                    orderId
            );
        }).toList();

        return PageResponse.<MyBidItem>builder()
                .content(content)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .build();
    }
}

package com.omnibid.support;

import com.omnibid.dto.auction.AuctionCreateRequest;
import com.omnibid.entity.*;
import com.omnibid.enums.*;
import com.omnibid.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class Seed {

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final ShipmentRepository shipmentRepository;
    private final ShipmentEventRepository shipmentEventRepository;
    private final ReportRepository reportRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public static final String DEFAULT_PASSWORD = "password123";

    public User createUser(String email, String fullName, UserRole role, UserStatus status) {
        User user = User.builder()
                .email(email)
                .fullName(fullName)
                .passwordHash(passwordEncoder.encode(DEFAULT_PASSWORD))
                .phoneNumber("0987654321")
                .address("123 Test Street, Hanoi")
                .role(role)
                .status(status)
                .build();
        return userRepository.save(user);
    }

    public Category createCategory(String name, String slug) {
        Category category = Category.builder()
                .name(name)
                .slug(slug)
                .build();
        return categoryRepository.save(category);
    }

    public Product createProduct(User seller, Category category, String title, ProductStatus status) {
        Product product = Product.builder()
                .sellerId(seller.getId())
                .categoryId(category.getId())
                .title(title)
                .description("Test description for " + title)
                .condition(ProductCondition.NEW)
                .status(status)
                .build();
        return productRepository.save(product);
    }

    public Auction createAuction(User seller, Product product, BigDecimal startPrice,
                                 BigDecimal bidIncrement, LocalDateTime startTime,
                                 LocalDateTime endTime, AuctionStatus status) {
        Auction auction = Auction.builder()
                .sellerId(seller.getId())
                .productId(product.getId())
                .startPrice(startPrice)
                .bidIncrement(bidIncrement)
                .currentPrice(startPrice)
                .bidCount(0)
                .startTime(startTime)
                .endTime(endTime)
                .status(status)
                .build();
        return auctionRepository.save(auction);
    }

    public Bid createBid(Auction auction, User bidder, BigDecimal amount, BidStatus status) {
        Bid bid = Bid.builder()
                .auctionId(auction.getId())
                .bidderId(bidder.getId())
                .amount(amount)
                .status(status)
                .build();
        Bid saved = bidRepository.save(bid);

        if (status == BidStatus.LEADING) {
            auction.setHighestBidderId(bidder.getId());
            auction.setCurrentPrice(amount);
            auction.setBidCount(auction.getBidCount() + 1);
            auctionRepository.save(auction);
        }
        return saved;
    }

    public Order orderPendingPayment(User buyer, User seller) {
        Category cat = categoryRepository.findBySlug("electronics")
                .orElseGet(() -> createCategory("Electronics", "electronics"));
        Product product = createProduct(seller, cat, "Auction Phone", ProductStatus.RESERVED);

        LocalDateTime now = LocalDateTime.now(clock);
        Auction auction = Auction.builder()
                .sellerId(seller.getId())
                .productId(product.getId())
                .startPrice(new BigDecimal("500000"))
                .bidIncrement(new BigDecimal("10000"))
                .currentPrice(new BigDecimal("530000"))
                .highestBidderId(buyer.getId())
                .winnerId(buyer.getId())
                .finalPrice(new BigDecimal("530000"))
                .bidCount(1)
                .startTime(now.minusHours(2))
                .endTime(now.minusMinutes(1))
                .closedAt(now.minusMinutes(1))
                .status(AuctionStatus.ENDED)
                .build();
        auction = auctionRepository.save(auction);

        Bid bid = Bid.builder()
                .auctionId(auction.getId())
                .bidderId(buyer.getId())
                .amount(new BigDecimal("530000"))
                .status(BidStatus.WON)
                .build();
        bidRepository.save(bid);

        Order order = Order.builder()
                .auctionId(auction.getId())
                .buyerId(buyer.getId())
                .sellerId(seller.getId())
                .productId(product.getId())
                .finalPrice(new BigDecimal("530000"))
                .status(OrderStatus.PENDING_PAYMENT)
                .shippingAddress("123 Buyer St")
                .phoneNumber("0987654321")
                .paymentDeadline(auction.getClosedAt().plusHours(48))
                .build();
        return orderRepository.save(order);
    }

    public Order orderPaid(User buyer, User seller) {
        Order order = orderPendingPayment(buyer, seller);
        order.setStatus(OrderStatus.PAID);
        order = orderRepository.save(order);

        Payment payment = Payment.builder()
                .orderId(order.getId())
                .amount(order.getFinalPrice())
                .method(PaymentMethod.MOCK_CARD)
                .status(PaymentStatus.SUCCESS)
                .idempotencyKey("KEY-" + System.nanoTime())
                .attemptNo(1)
                .paidAt(LocalDateTime.now(clock))
                .transactionRef("TX-" + System.nanoTime())
                .build();
        paymentRepository.save(payment);

        Shipment shipment = Shipment.builder()
                .orderId(order.getId())
                .carrierName("SIMULATED")
                .trackingCode("SIM-" + System.nanoTime())
                .status(ShipmentStatus.CREATED)
                .build();
        shipment = shipmentRepository.save(shipment);

        ShipmentEvent event = ShipmentEvent.builder()
                .shipmentId(shipment.getId())
                .status(ShipmentStatus.CREATED)
                .note("Shipment initiated")
                .build();
        shipmentEventRepository.save(event);

        return order;
    }

    public Order orderDelivered(User buyer, User seller) {
        Order order = orderPaid(buyer, seller);
        order.setStatus(OrderStatus.SHIPPING);
        order.setConfirmDeadline(LocalDateTime.now(clock).plusDays(7));
        order = orderRepository.save(order);

        Shipment shipment = shipmentRepository.findByOrderId(order.getId()).orElseThrow();
        shipment.setStatus(ShipmentStatus.DELIVERED);
        shipment = shipmentRepository.save(shipment);

        ShipmentEvent deliveredEvent = ShipmentEvent.builder()
                .shipmentId(shipment.getId())
                .status(ShipmentStatus.DELIVERED)
                .note("Package delivered")
                .build();
        shipmentEventRepository.save(deliveredEvent);

        return order;
    }
}

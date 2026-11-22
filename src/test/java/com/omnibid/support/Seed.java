package com.omnibid.support;

import com.omnibid.entity.*;
import com.omnibid.enums.*;
import com.omnibid.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class Seed {

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public static final String DEFAULT_PASSWORD = "Password123";

    public User createUser(String email, String fullName, UserRole role, UserStatus status) {
        User user = User.builder()
                .email(email)
                .fullName(fullName)
                .passwordHash(passwordEncoder.encode(DEFAULT_PASSWORD))
                .phoneNumber("0987654321")
                .address("123 Test Street, Hanoi")
                .role(role)
                .status(status)
                .tokenVersion(0)
                .build();
        return userRepository.save(user);
    }

    public Category createCategory(String name, String slug) {
        Category category = Category.builder()
                .name(name)
                .slug(slug)
                .icon("laptop")
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
        Product saved = productRepository.save(product);
        ProductImage image = ProductImage.builder()
                .productId(saved.getId())
                .imageUrl("https://res.cloudinary.com/demo/image/upload/omnibid/products/" + saved.getId() + ".png")
                .publicId("omnibid/products/seed_" + saved.getId() + "_" + UUID.randomUUID())
                .isPrimary(true)
                .build();
        productImageRepository.save(image);
        return saved;
    }

    public Auction createAuction(User seller, Product product, BigDecimal startPrice,
                                 BigDecimal bidIncrement, Instant startTime,
                                 Instant endTime, AuctionStatus status) {
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

    public Bid createBid(Auction auction, User bidder, BigDecimal amount) {
        Bid bid = Bid.builder()
                .auctionId(auction.getId())
                .bidderId(bidder.getId())
                .amount(amount)
                .build();
        Bid saved = bidRepository.save(bid);

        auction.setHighestBidderId(bidder.getId());
        auction.setCurrentPrice(amount);
        auction.setBidCount(auction.getBidCount() + 1);
        auctionRepository.save(auction);
        return saved;
    }

    public Order orderPendingPayment(User buyer, User seller) {
        Category cat = categoryRepository.findBySlug("electronics")
                .orElseGet(() -> createCategory("Electronics", "electronics"));
        Product product = createProduct(seller, cat, "Auction Phone", ProductStatus.RESERVED);

        Instant now = clock.instant();
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
                .startTime(now.minus(2, ChronoUnit.HOURS))
                .endTime(now.minus(1, ChronoUnit.MINUTES))
                .closedAt(now.minus(1, ChronoUnit.MINUTES))
                .status(AuctionStatus.ENDED)
                .build();
        auction = auctionRepository.save(auction);

        Bid bid = Bid.builder()
                .auctionId(auction.getId())
                .bidderId(buyer.getId())
                .amount(new BigDecimal("530000"))
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
                .paymentDeadline(auction.getClosedAt().plus(48, ChronoUnit.HOURS))
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
                .idempotencyKey(UUID.randomUUID().toString())
                .attemptNo(1)
                .paidAt(clock.instant())
                .build();
        paymentRepository.save(payment);

        return order;
    }

    public Order orderShipping(User buyer, User seller) {
        Order order = orderPaid(buyer, seller);
        Instant now = clock.instant();
        order.setStatus(OrderStatus.SHIPPING);
        order.setTrackingCode("VN123456789");
        order.setShippedAt(now);
        order.setConfirmDeadline(now.plus(7, ChronoUnit.DAYS));
        return orderRepository.save(order);
    }
}

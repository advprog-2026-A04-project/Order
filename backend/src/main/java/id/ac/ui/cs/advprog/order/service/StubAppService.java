package id.ac.ui.cs.advprog.order.service;

import id.ac.ui.cs.advprog.order.common.ApiException;
import id.ac.ui.cs.advprog.order.common.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class StubAppService {

    public record UserView(Long id, String username, String email, String fullName, String role) {}

    public record ProductView(
            Long id,
            String name,
            String description,
            String category,
            BigDecimal price,
            int stock,
            String origin,
            String imageUrl,
            String jastiperName,
            String jastiperUsername,
            Instant purchaseDate,
            Instant returnDate,
            Double avgRating,
            int reviewCount
    ) {}

    public record ReviewView(
            Long id,
            Long productId,
            Long userId,
            int rating,
            String comment,
            Instant createdAt
    ) {}

    public record WalletView(Long userId, BigDecimal balance) {}

    private static final BigDecimal DEFAULT_BALANCE = BigDecimal.valueOf(25_000_000L);

    private final AtomicLong userIdSeq = new AtomicLong(1000);
    private final AtomicLong reviewIdSeq = new AtomicLong(1);

    private final Map<Long, ProductSeed> products = new LinkedHashMap<>();
    private final Map<String, StubUser> usersByEmail = new ConcurrentHashMap<>();
    private final Map<Long, BigDecimal> wallets = new ConcurrentHashMap<>();
    private final Map<Long, CopyOnWriteArrayList<ReviewView>> reviewsByProduct = new ConcurrentHashMap<>();

    public StubAppService() {
        seedProducts();
        seedUsers();
        seedReviews();
    }

    public UserView register(String name, String email, String password) {
        String normalizedName = safeTrim(name);
        String normalizedEmail = normalizeEmail(email);
        String rawPassword = safeTrim(password);

        if (normalizedName.isBlank() || normalizedEmail.isBlank() || rawPassword.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "name/email/password wajib diisi");
        }

        synchronized (this) {
            if (usersByEmail.containsKey(normalizedEmail)) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "Email sudah terdaftar");
            }

            long userId = userIdSeq.incrementAndGet();
            String username = buildUsername(normalizedName, userId);
            StubUser user = new StubUser(userId, username, normalizedEmail, normalizedName, "TITIPER", rawPassword);
            usersByEmail.put(normalizedEmail, user);
            wallets.put(userId, DEFAULT_BALANCE);
            return user.toView();
        }
    }

    public UserView login(String email, String password) {
        String normalizedEmail = normalizeEmail(email);
        String rawPassword = safeTrim(password);
        StubUser user = usersByEmail.get(normalizedEmail);

        if (user == null || !user.password.equals(rawPassword)) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, "Email atau password salah");
        }
        return user.toView();
    }

    public WalletView getWallet(Long userId) {
        if (userId == null) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "X-User-Id wajib diisi");
        }
        BigDecimal balance = wallets.computeIfAbsent(userId, id -> DEFAULT_BALANCE);
        return new WalletView(userId, balance);
    }

    public WalletView topup(Long userId, BigDecimal amount) {
        if (userId == null) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "X-User-Id wajib diisi");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "amount harus > 0");
        }
        wallets.merge(userId, amount, BigDecimal::add);
        return new WalletView(userId, wallets.get(userId));
    }

    public List<ProductView> listProducts() {
        List<ProductView> result = new ArrayList<>();
        for (ProductSeed seed : products.values()) {
            result.add(toView(seed));
        }
        return result;
    }

    public ProductView getProduct(Long productId) {
        ProductSeed seed = products.get(productId);
        if (seed == null) {
            throw new ApiException(ErrorCode.PRODUCT_NOT_FOUND, HttpStatus.NOT_FOUND, "Produk tidak ditemukan");
        }
        return toView(seed);
    }

    public List<ReviewView> listReviews(Long productId) {
        ensureProductExists(productId);
        return new ArrayList<>(reviewsByProduct.getOrDefault(productId, new CopyOnWriteArrayList<>()));
    }

    public ReviewView addReview(Long userId, Long productId, int rating, String comment) {
        ensureProductExists(productId);
        if (userId == null) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, "X-User-Id wajib diisi");
        }
        if (rating < 1 || rating > 5) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "rating harus 1-5");
        }

        ReviewView created = new ReviewView(
                reviewIdSeq.getAndIncrement(),
                productId,
                userId,
                rating,
                safeTrim(comment),
                Instant.now()
        );

        reviewsByProduct.computeIfAbsent(productId, ignored -> new CopyOnWriteArrayList<>()).add(0, created);
        return created;
    }

    private void ensureProductExists(Long productId) {
        if (productId == null || !products.containsKey(productId)) {
            throw new ApiException(ErrorCode.PRODUCT_NOT_FOUND, HttpStatus.NOT_FOUND, "Produk tidak ditemukan");
        }
    }

    private ProductView toView(ProductSeed seed) {
        List<ReviewView> reviews = reviewsByProduct.getOrDefault(seed.id, new CopyOnWriteArrayList<>());
        int reviewCount = reviews.size();
        Double avgRating = null;
        if (reviewCount > 0) {
            double avg = reviews.stream().mapToInt(ReviewView::rating).average().orElse(0.0);
            avgRating = BigDecimal.valueOf(avg).setScale(1, RoundingMode.HALF_UP).doubleValue();
        }
        return new ProductView(
                seed.id,
                seed.name,
                seed.description,
                seed.category,
                seed.price,
                seed.stock,
                seed.origin,
                seed.imageUrl,
                seed.jastiperName,
                seed.jastiperUsername,
                seed.purchaseDate,
                seed.returnDate,
                avgRating,
                reviewCount
        );
    }

    private void seedUsers() {
        addSeedUser(2L, "titiper", "titiper@example.com", "Titiper Demo", "TITIPER", "titiper123", BigDecimal.valueOf(50_000_000L));
        addSeedUser(3L, "jastiper", "jastiper@example.com", "Jastiper Demo", "JASTIPER", "jastiper123", BigDecimal.valueOf(7_500_000L));
        addSeedUser(4L, "admin", "admin@example.com", "Admin Demo", "ADMIN", "admin123", BigDecimal.valueOf(99_000_000L));
        userIdSeq.set(Math.max(userIdSeq.get(), 4L));
    }

    private void addSeedUser(Long id, String username, String email, String fullName, String role, String password, BigDecimal balance) {
        usersByEmail.put(email.toLowerCase(Locale.ROOT), new StubUser(id, username, email.toLowerCase(Locale.ROOT), fullName, role, password));
        wallets.put(id, balance);
    }

    private void seedProducts() {
        Instant now = Instant.now();
        addProduct(1L, "Nike SB Dunk Low Travis Scott", "Sneakers kolaborasi limited dengan detail premium.", "Sneakers",
                BigDecimal.valueOf(4_500_000L), 12, "US",
                "https://images.unsplash.com/photo-1600185365483-26d7a4cc7519?auto=format&fit=crop&w=900&q=80",
                "Rama Pratama", "rama_jastip", now.minus(20, ChronoUnit.DAYS), now.plus(10, ChronoUnit.DAYS));
        addProduct(2L, "Adidas Samba OG Wales Bonner", "Sneakers vintage style yang lagi hype.", "Sneakers",
                BigDecimal.valueOf(3_250_000L), 9, "UK",
                "https://images.unsplash.com/photo-1549298916-b41d501d3772?auto=format&fit=crop&w=900&q=80",
                "Rama Pratama", "rama_jastip", now.minus(12, ChronoUnit.DAYS), now.plus(14, ChronoUnit.DAYS));
        addProduct(3L, "Coldplay Concert Ticket CAT 1", "Tiket konser resmi, e-ticket transfer.", "Tiket Konser",
                BigDecimal.valueOf(2_100_000L), 6, "SG",
                "https://images.unsplash.com/photo-1506157786151-b8491531f063?auto=format&fit=crop&w=900&q=80",
                "Maya Lestari", "maya_jastip", now.minus(2, ChronoUnit.DAYS), now.plus(21, ChronoUnit.DAYS));
        addProduct(4L, "Taylor Swift The Eras Tour Ticket", "Seat strategic, siap transfer resmi.", "Tiket Konser",
                BigDecimal.valueOf(6_850_000L), 3, "JP",
                "https://images.unsplash.com/photo-1459749411175-04bf5292ceea?auto=format&fit=crop&w=900&q=80",
                "Maya Lestari", "maya_jastip", now.minus(1, ChronoUnit.DAYS), now.plus(18, ChronoUnit.DAYS));
        addProduct(5L, "Dior Addict Lip Glow Set", "Limited beauty set dari duty free.", "Beauty",
                BigDecimal.valueOf(1_350_000L), 20, "FR",
                "https://images.unsplash.com/photo-1522335789203-aabd1fc54bc9?auto=format&fit=crop&w=900&q=80",
                "Nadia K", "nadia_jastip", now.minus(7, ChronoUnit.DAYS), now.plus(25, ChronoUnit.DAYS));
        addProduct(6L, "Rare Sonny Angel Winter Wonderland", "Collectible blind box edisi langka.", "Collectible",
                BigDecimal.valueOf(780_000L), 15, "KR",
                "https://images.unsplash.com/photo-1596462502278-27bfdc403348?auto=format&fit=crop&w=900&q=80",
                "Nadia K", "nadia_jastip", now.minus(10, ChronoUnit.DAYS), now.plus(30, ChronoUnit.DAYS));
    }

    private void addProduct(Long id,
                            String name,
                            String description,
                            String category,
                            BigDecimal price,
                            int stock,
                            String origin,
                            String imageUrl,
                            String jastiperName,
                            String jastiperUsername,
                            Instant purchaseDate,
                            Instant returnDate) {
        products.put(id, new ProductSeed(
                id, name, description, category, price, stock, origin, imageUrl, jastiperName, jastiperUsername, purchaseDate, returnDate
        ));
    }

    private void seedReviews() {
        addSeedReview(1L, 2L, 5, "Packing rapi dan cepat sampai.");
        addSeedReview(1L, 3L, 4, "Original, sesuai foto.");
        addSeedReview(2L, 2L, 5, "Aman dan trusted.");
        addSeedReview(3L, 4L, 5, "Tiket valid, masuk venue tanpa masalah.");
    }

    private void addSeedReview(Long productId, Long userId, int rating, String comment) {
        reviewsByProduct.computeIfAbsent(productId, ignored -> new CopyOnWriteArrayList<>()).add(
                new ReviewView(reviewIdSeq.getAndIncrement(), productId, userId, rating, comment, Instant.now().minus(3, ChronoUnit.DAYS))
        );
    }

    private String buildUsername(String fullName, long userId) {
        String base = fullName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (base.isBlank()) {
            base = "user";
        }
        return base + userId;
    }

    private String normalizeEmail(String email) {
        return safeTrim(email).toLowerCase(Locale.ROOT);
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private static final class StubUser {
        private final Long id;
        private final String username;
        private final String email;
        private final String fullName;
        private final String role;
        private final String password;

        private StubUser(Long id, String username, String email, String fullName, String role, String password) {
            this.id = id;
            this.username = username;
            this.email = email;
            this.fullName = fullName;
            this.role = role;
            this.password = password;
        }

        private UserView toView() {
            return new UserView(id, username, email, fullName, role);
        }
    }

    private record ProductSeed(
            Long id,
            String name,
            String description,
            String category,
            BigDecimal price,
            int stock,
            String origin,
            String imageUrl,
            String jastiperName,
            String jastiperUsername,
            Instant purchaseDate,
            Instant returnDate
    ) {}
}


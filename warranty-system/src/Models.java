import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/** Requirement 1: Records - products and customers */
class Product implements Serializable {
    int id;
    String name, brand, category;
    int defaultMonths;
}

class Customer implements Serializable {
    int id;
    String name, phone, email;
}

/** Requirement 2: Registration - product + customer + serial + duration */
class Registration implements Serializable {
    int id, productId, customerId, months;
    String serial;
    LocalDate purchaseDate;

    LocalDate expiry() { return purchaseDate.plusMonths(months); }

    /** Requirement 3: Warranty status is always computed live from today's date */
    boolean isActive() { return !LocalDate.now().isAfter(expiry()); }

    long daysLeft() { return ChronoUnit.DAYS.between(LocalDate.now(), expiry()); }

    int percentUsed() {
        long total = ChronoUnit.DAYS.between(purchaseDate, expiry());
        long used = ChronoUnit.DAYS.between(purchaseDate, LocalDate.now());
        if (total <= 0) return 100;
        return (int) Math.max(0, Math.min(100, used * 100 / total));
    }
}

class ClaimEvent implements Serializable {
    String status, note;
    LocalDateTime at;
    ClaimEvent(String status, String note) {
        this.status = status; this.note = note; this.at = LocalDateTime.now();
    }
}

/** Requirement 4 and 5: Claims with a tracked timeline */
class Claim implements Serializable {
    int id, registrationId;
    String issue, status;           // Pending | Approved | Rejected
    LocalDateTime createdAt;
    List<ClaimEvent> timeline = new ArrayList<>();
}

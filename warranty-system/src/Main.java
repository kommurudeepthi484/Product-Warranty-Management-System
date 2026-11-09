import com.sun.net.httpserver.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Product Warranty Management System - backend (REST API + static file server).
 * Run this file, then open http://localhost:8080
 */
public class Main {
    static final Store db = Store.load();
    static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");

    static class ApiError extends RuntimeException {
        final int code;
        ApiError(int code, String msg) { super(msg); this.code = code; }
    }

    public static void main(String[] args) throws Exception {
        int port = 8080;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api", Main::handleApi);
        server.createContext("/", Main::handleStatic);
        server.start();
        System.out.println("==============================================");
        System.out.println(" Warranty System running -> http://localhost:" + port);
        System.out.println("==============================================");
    }

    // ---------------------------------------------------------------- static files
    static void handleStatic(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        if (path.equals("/")) path = "/index.html";
        Path file = Paths.get("web", path).normalize();
        if (!file.startsWith("web") || !Files.isRegularFile(file)) { send(ex, 404, "text/plain", "Not found"); return; }
        String name = file.toString();
        String type = name.endsWith(".html") ? "text/html" : name.endsWith(".css") ? "text/css"
                : name.endsWith(".js") ? "application/javascript" : "application/octet-stream";
        send(ex, 200, type + "; charset=utf-8", new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
    }

    static void send(HttpExchange ex, int code, String type, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", type);
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(code, b.length);
        try (OutputStream o = ex.getResponseBody()) { o.write(b); }
    }

    // ---------------------------------------------------------------- API router
    static synchronized void handleApi(HttpExchange ex) throws IOException {
        try {
            String method = ex.getRequestMethod();
            String path = ex.getRequestURI().getPath().substring(4);   // strip "/api"
            Map<String, String> q = query(ex.getRequestURI().getRawQuery());
            Map<String, String> body = Collections.emptyMap();
            if (method.equals("POST") || method.equals("PUT"))
                body = Json.parse(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String out = route(method, path, q, body);
            db.save();
            send(ex, 200, "application/json; charset=utf-8", out);
        } catch (ApiError e) {
            send(ex, e.code, "application/json; charset=utf-8", Json.obj("error", e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            send(ex, 500, "application/json; charset=utf-8", Json.obj("error", "Server error: " + e.getMessage()));
        }
    }

    static String route(String m, String p, Map<String, String> q, Map<String, String> b) {
        if (p.equals("/stats") && m.equals("GET")) return stats();
        if (p.equals("/demo") && m.equals("POST")) { db.seedDemo(); return Json.obj("ok", true); }

        if (p.equals("/products")) {
            if (m.equals("GET")) return Json.arr(db.products.values().stream().map(Main::productJson).collect(Collectors.toList()));
            if (m.equals("POST")) return addProduct(b);
        }
        if (p.startsWith("/products/") && m.equals("DELETE")) {
            int id = Integer.parseInt(p.substring(10));
            if (db.registrations.values().stream().anyMatch(r -> r.productId == id))
                throw new ApiError(409, "This product has registered warranties and cannot be deleted.");
            db.products.remove(id); return Json.obj("ok", true);
        }

        if (p.equals("/customers")) {
            if (m.equals("GET")) return Json.arr(db.customers.values().stream().map(Main::customerJson).collect(Collectors.toList()));
            if (m.equals("POST")) return addCustomer(b);
        }
        if (p.startsWith("/customers/") && m.equals("DELETE")) {
            int id = Integer.parseInt(p.substring(11));
            if (db.registrations.values().stream().anyMatch(r -> r.customerId == id))
                throw new ApiError(409, "This customer has registered warranties and cannot be deleted.");
            db.customers.remove(id); return Json.obj("ok", true);
        }

        if (p.equals("/registrations")) {
            if (m.equals("GET")) return listRegistrations(q);
            if (m.equals("POST")) return addRegistration(b);
        }
        if (p.equals("/check") && m.equals("GET")) {                    // live warranty check by serial
            Registration r = db.bySerial(q.get("serial"));
            if (r == null) return Json.obj("found", false);
            return Json.obj("found", true, "registration", new Json.Raw(regJson(r)));
        }

        if (p.equals("/claims")) {
            if (m.equals("GET")) return listClaims(q);
            if (m.equals("POST")) return addClaim(b);
        }
        if (p.startsWith("/claims/") && m.equals("PUT")) return updateClaim(Integer.parseInt(p.substring(8)), b);

        throw new ApiError(404, "Unknown endpoint: " + m + " " + p);
    }

    // ---------------------------------------------------------------- Requirement 1: Records
    static String addProduct(Map<String, String> b) {
        need(b, "name"); need(b, "brand");
        Product x = new Product(); x.id = db.id(); x.name = b.get("name").trim(); x.brand = b.get("brand").trim();
        x.category = b.getOrDefault("category", "General"); x.defaultMonths = intOf(b.get("defaultMonths"), 12);
        db.products.put(x.id, x); return productJson(x);
    }

    static String addCustomer(Map<String, String> b) {
        need(b, "name"); need(b, "phone");
        Customer x = new Customer(); x.id = db.id(); x.name = b.get("name").trim(); x.phone = b.get("phone").trim();
        x.email = b.getOrDefault("email", "");
        db.customers.put(x.id, x); return customerJson(x);
    }

    // ---------------------------------------------------------------- Requirement 2: Registration
    static String addRegistration(Map<String, String> b) {
        need(b, "productId"); need(b, "customerId"); need(b, "serial"); need(b, "purchaseDate"); need(b, "months");
        int pid = intOf(b.get("productId"), -1), cid = intOf(b.get("customerId"), -1);
        if (!db.products.containsKey(pid)) throw new ApiError(400, "Select a valid product.");
        if (!db.customers.containsKey(cid)) throw new ApiError(400, "Select a valid customer.");
        if (db.bySerial(b.get("serial")) != null) throw new ApiError(409, "Serial number already registered.");
        int months = intOf(b.get("months"), 0);
        if (months < 1) throw new ApiError(400, "Warranty duration must be at least 1 month.");
        Registration r = new Registration(); r.id = db.id(); r.productId = pid; r.customerId = cid;
        r.serial = b.get("serial").trim().toUpperCase(); r.months = months;
        try { r.purchaseDate = LocalDate.parse(b.get("purchaseDate")); } catch (Exception e) { throw new ApiError(400, "Invalid purchase date."); }
        if (r.purchaseDate.isAfter(LocalDate.now())) throw new ApiError(400, "Purchase date cannot be in the future.");
        db.registrations.put(r.id, r); return regJson(r);
    }

    // ---------------------------------------------------------------- Requirement 3: Warranty status + search
    static String listRegistrations(Map<String, String> q) {
        String s = q.getOrDefault("q", "").toLowerCase().trim();
        String status = q.getOrDefault("status", "");
        List<String> out = new ArrayList<>();
        List<Registration> all = new ArrayList<>(db.registrations.values());
        Collections.reverse(all);
        for (Registration r : all) {
            Product p = db.products.get(r.productId); Customer c = db.customers.get(r.customerId);
            String hay = (p.name + " " + p.brand + " " + r.serial + " " + c.name + " " + c.phone).toLowerCase();
            if (!s.isEmpty() && !hay.contains(s)) continue;
            if (!status.isEmpty() && !status.equals(r.isActive() ? "Active" : "Expired")) continue;
            out.add(regJson(r));
        }
        return Json.arr(out);
    }

    // ---------------------------------------------------------------- Requirement 4 + 5: Claims
    static String addClaim(Map<String, String> b) {
        need(b, "serial"); need(b, "issue");
        Registration r = db.bySerial(b.get("serial"));
        if (r == null) throw new ApiError(404, "No warranty is registered for this serial number.");
        // ELIGIBILITY RULE: claims are accepted ONLY for Active warranties
        if (!r.isActive())
            throw new ApiError(400, "Claim not accepted: warranty expired on " + r.expiry() + ".");
        if (b.get("issue").trim().length() < 5) throw new ApiError(400, "Please describe the issue (min 5 characters).");
        Claim c = new Claim(); c.id = db.id(); c.registrationId = r.id; c.issue = b.get("issue").trim();
        c.status = "Pending"; c.createdAt = java.time.LocalDateTime.now();
        c.timeline.add(new ClaimEvent("Pending", "Claim submitted by customer"));
        db.claims.put(c.id, c); return claimJson(c);
    }

    static String updateClaim(int id, Map<String, String> b) {
        Claim c = db.claims.get(id);
        if (c == null) throw new ApiError(404, "Claim not found.");
        String st = b.get("status");
        if (!List.of("Pending", "Approved", "Rejected").contains(st)) throw new ApiError(400, "Status must be Pending, Approved or Rejected.");
        if (st.equals(c.status)) throw new ApiError(400, "Claim is already " + st + ".");
        c.status = st;
        c.timeline.add(new ClaimEvent(st, b.getOrDefault("note", "").isBlank() ? "Status changed to " + st : b.get("note")));
        return claimJson(c);
    }

    static String listClaims(Map<String, String> q) {
        String status = q.getOrDefault("status", ""), s = q.getOrDefault("q", "").toLowerCase().trim();
        List<Claim> all = new ArrayList<>(db.claims.values()); Collections.reverse(all);
        List<String> out = new ArrayList<>();
        for (Claim c : all) {
            if (!status.isEmpty() && !status.equals(c.status)) continue;
            Registration r = db.registrations.get(c.registrationId);
            Product p = db.products.get(r.productId); Customer cu = db.customers.get(r.customerId);
            String hay = (p.name + " " + r.serial + " " + cu.name + " " + c.issue).toLowerCase();
            if (!s.isEmpty() && !hay.contains(s)) continue;
            out.add(claimJson(c));
        }
        return Json.arr(out);
    }

    // ---------------------------------------------------------------- dashboard
    static String stats() {
        long active = db.registrations.values().stream().filter(Registration::isActive).count();
        long soon = db.registrations.values().stream().filter(r -> r.isActive() && r.daysLeft() <= 30).count();
        Map<String, Long> cl = db.claims.values().stream().collect(Collectors.groupingBy(c -> c.status, Collectors.counting()));
        return Json.obj("products", db.products.size(), "customers", db.customers.size(),
                "registrations", db.registrations.size(), "active", active,
                "expired", db.registrations.size() - active, "expiringSoon", soon,
                "pending", cl.getOrDefault("Pending", 0L), "approved", cl.getOrDefault("Approved", 0L),
                "rejected", cl.getOrDefault("Rejected", 0L));
    }

    // ---------------------------------------------------------------- JSON builders
    static String productJson(Product p) {
        return Json.obj("id", p.id, "name", p.name, "brand", p.brand, "category", p.category, "defaultMonths", p.defaultMonths);
    }
    static String customerJson(Customer c) {
        return Json.obj("id", c.id, "name", c.name, "phone", c.phone, "email", c.email);
    }
    static String regJson(Registration r) {
        Product p = db.products.get(r.productId); Customer c = db.customers.get(r.customerId);
        return Json.obj("id", r.id, "serial", r.serial, "productName", p.name, "brand", p.brand, "category", p.category,
                "customerName", c.name, "customerPhone", c.phone, "purchaseDate", r.purchaseDate.toString(),
                "months", r.months, "expiryDate", r.expiry().toString(), "status", r.isActive() ? "Active" : "Expired",
                "daysLeft", r.daysLeft(), "percentUsed", r.percentUsed(), "expiringSoon", r.isActive() && r.daysLeft() <= 30);
    }
    static String claimJson(Claim c) {
        Registration r = db.registrations.get(c.registrationId);
        Product p = db.products.get(r.productId); Customer cu = db.customers.get(r.customerId);
        List<String> tl = c.timeline.stream().map(e -> Json.obj("status", e.status, "note", e.note, "at", e.at.format(TS))).collect(Collectors.toList());
        return Json.obj("id", c.id, "serial", r.serial, "productName", p.name, "customerName", cu.name,
                "issue", c.issue, "status", c.status, "createdAt", c.createdAt.format(TS),
                "timeline", new Json.Raw(Json.arr(tl)));
    }

    // ---------------------------------------------------------------- helpers
    static void need(Map<String, String> b, String k) {
        if (b.get(k) == null || b.get(k).isBlank()) throw new ApiError(400, "Missing field: " + k);
    }
    static int intOf(String s, int def) { try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; } }
    static Map<String, String> query(String raw) {
        Map<String, String> m = new HashMap<>();
        if (raw == null) return m;
        for (String kv : raw.split("&")) {
            String[] a = kv.split("=", 2);
            m.put(URLDecoder.decode(a[0], StandardCharsets.UTF_8), a.length > 1 ? URLDecoder.decode(a[1], StandardCharsets.UTF_8) : "");
        }
        return m;
    }
}

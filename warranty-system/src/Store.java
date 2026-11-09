import java.io.*;
import java.time.LocalDate;
import java.util.*;

/** In-memory database that auto-saves to data.ser so data survives restarts. */
class Store implements Serializable {
    private static final String FILE = "data.ser";
    final Map<Integer, Product> products = new LinkedHashMap<>();
    final Map<Integer, Customer> customers = new LinkedHashMap<>();
    final Map<Integer, Registration> registrations = new LinkedHashMap<>();
    final Map<Integer, Claim> claims = new LinkedHashMap<>();
    int nextId = 1;

    int id() { return nextId++; }

    static Store load() {
        File f = new File(FILE);
        if (f.exists()) {
            try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(f))) {
                return (Store) in.readObject();
            } catch (Exception e) { System.out.println("Could not read " + FILE + ", starting fresh."); }
        }
        return new Store();
    }

    void save() {
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(FILE))) {
            out.writeObject(this);
        } catch (IOException e) { System.out.println("Save failed: " + e.getMessage()); }
    }

    Registration bySerial(String serial) {
        if (serial == null) return null;
        for (Registration r : registrations.values())
            if (r.serial.equalsIgnoreCase(serial.trim())) return r;
        return null;
    }

    void seedDemo() {
        products.clear(); customers.clear(); registrations.clear(); claims.clear(); nextId = 1;
        String[][] p = {{"Galaxy S24","Samsung","Mobile","12"},{"OLED TV 55\"","LG","Television","24"},
                        {"MacBook Air M3","Apple","Laptop","12"},{"Inverter AC 1.5T","Voltas","Air Conditioner","36"}};
        for (String[] x : p) { Product o = new Product(); o.id = id(); o.name = x[0]; o.brand = x[1]; o.category = x[2]; o.defaultMonths = Integer.parseInt(x[3]); products.put(o.id, o); }
        String[][] c = {{"Ravi Kumar","9876543210","ravi@mail.com"},{"Sneha Reddy","9123456780","sneha@mail.com"},{"Arjun Rao","9988776655","arjun@mail.com"}};
        for (String[] x : c) { Customer o = new Customer(); o.id = id(); o.name = x[0]; o.phone = x[1]; o.email = x[2]; customers.put(o.id, o); }
        // {productId, customerId, serial, daysAgoPurchased, months}
        Object[][] r = {{1,5,"SN-S24-1001",60,12},{2,6,"SN-LG-2002",800,24},{3,7,"SN-MAC-3003",200,12},{4,5,"SN-AC-4004",1070,36},{1,6,"SN-S24-1005",340,12}};
        for (Object[] x : r) {
            Registration o = new Registration(); o.id = id(); o.productId = (int) x[0]; o.customerId = (int) x[1];
            o.serial = (String) x[2]; o.purchaseDate = LocalDate.now().minusDays((int) x[3]); o.months = (int) x[4];
            registrations.put(o.id, o);
        }
        // fix ids: products are 1-4, customers 5-7 (ids are shared counter)
        Claim cl = new Claim(); cl.id = id(); cl.registrationId = bySerial("SN-S24-1001").id; cl.issue = "Screen flickers when charging";
        cl.status = "Pending"; cl.createdAt = java.time.LocalDateTime.now().minusHours(3);
        cl.timeline.add(new ClaimEvent("Pending", "Claim submitted by customer")); claims.put(cl.id, cl);
        save();
    }
}

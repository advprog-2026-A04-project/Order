package id.ac.ui.cs.advprog.order.common;

public enum Role {
    TITIPER,
    JASTIPER,
    ADMIN;

    public static Role fromHeader(String raw) {
        if (raw == null || raw.isBlank()) return TITIPER;

        String r = raw.trim().toUpperCase();

        if (r.equals("BUYER")) return TITIPER;

        return Role.valueOf(r);
    }
}
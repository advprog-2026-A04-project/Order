package id.ac.ui.cs.advprog.order.integration;

final class IntegrationConfigValues {
    private IntegrationConfigValues() {
    }

    static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\uFEFF", "").trim();
    }
}

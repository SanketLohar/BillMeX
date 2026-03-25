public class TestLink {
    public static void main(String[] args) {
        String frontendUrl = "http://localhost:5501";
        String invoiceNumber = "INV-2024-001";
        String token = "abc123xyz";
        String payLink = frontendUrl
                    + "/Face_Payment-Shravani_UI/pay-invoice.html?invoiceId="
                    + invoiceNumber
                    + "&token="
                    + token;
        System.out.println("Generated Pay Link: " + payLink);
    }
}

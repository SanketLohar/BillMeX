package com.billme.payment;

import com.billme.customer.CustomerProfile;
import com.billme.invoice.Invoice;
import com.billme.invoice.InvoiceStatus;
import com.billme.invoice.PaymentMethod;
import com.billme.repository.CustomerProfileRepository;
import com.billme.repository.InvoiceRepository;
import com.billme.repository.UserRepository;
import com.billme.security.face.FaceRecognitionUtil;
import com.billme.user.User;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FacePayService {

    private final InvoiceRepository invoiceRepository;
    private final UserRepository userRepository;
    private final CustomerProfileRepository customerProfileRepository;
    private final PaymentSettlementService settlementService;

    // 🔥 UPDATED THRESHOLD (STRICTER)
    private static final double FACE_MATCH_THRESHOLD = 0.97;

    @Transactional
    public String payInvoice(Long invoiceId, Object paymentEmbedding) {

        double[] embedding = FaceRecognitionUtil.parseEmbedding(paymentEmbedding);

        if (embedding.length != 128) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid embedding size. Expected 128, got " + embedding.length);
        }

        User customer = getLoggedInUser();

        Invoice invoice = invoiceRepository
                .findByIdAndCustomer_User_Id(invoiceId, customer.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found"));

        if (invoice.getStatus() == InvoiceStatus.PAID) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Invoice already paid");
        }

        CustomerProfile profile = customerProfileRepository
                .findByUser_Id(customer.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer profile not found"));

        // 🔥 IMPORTANT: log similarity for debugging
        double similarity = FaceRecognitionUtil.calculateSimilarity(
                profile.getFaceEmbeddings(),
                embedding
        );

        System.out.println("🔍 FACE SIMILARITY: " + similarity);

        if (similarity < FACE_MATCH_THRESHOLD) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Face verification failed");
        }

        invoice.setPaymentMethod(PaymentMethod.FACE_PAY);
        invoiceRepository.save(invoice);

        settlementService.settlePayment(invoiceId, "FACEPAY-" + UUID.randomUUID());

        return "FacePay successful";
    }

    private User getLoggedInUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }



    @Transactional
    public String payInvoicePublic(FacePayRequest request) {

        double[] embedding = FaceRecognitionUtil.parseEmbedding(request.getEmbedding());

        if (embedding.length != 128) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid embedding size");
        }

        // 🔥 Fetch invoice using secure token
        Invoice invoice = invoiceRepository
                .findByInvoiceNumberAndPaymentToken(
                        request.getInvoiceNumber(),
                        request.getToken()
                )
                .orElseThrow(() -> {
                    System.out.println("🚨 [UNAUTHORIZED] Invalid token access attempt for FacePay public invoice.");
                    return new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Invalid payment link"
                    );
                });

        if (invoice.getDueDate() != null && java.time.LocalDateTime.now().isAfter(invoice.getDueDate().atTime(23, 59))) {
            System.out.println("❌ [TOKEN EXPIRED] FacePay link expired for Invoice " + invoice.getInvoiceNumber());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment link has expired");
        }

        if (invoice.getStatus() == InvoiceStatus.PAID) {
            System.out.println("⚠️ [IDEMPOTENCY] Invoice " + invoice.getInvoiceNumber() + " already paid. Skipping FacePay.");
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Already paid");
        }

        User customer = invoice.getCustomer().getUser();

        CustomerProfile profile = customerProfileRepository
                .findByUser_Id(customer.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Profile not found"
                ));

        double similarity = FaceRecognitionUtil.calculateSimilarity(
                profile.getFaceEmbeddings(),
                embedding
        );

        System.out.println("🔍 FACE SIMILARITY: " + similarity);

        if (similarity < FACE_MATCH_THRESHOLD) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Face verification failed"
            );
        }

        invoice.setPaymentMethod(PaymentMethod.FACE_PAY);
        invoiceRepository.save(invoice);

        settlementService.settlePayment(
                invoice.getId(),
                "FACEPAY-" + UUID.randomUUID()
        );

        return "FacePay successful";
    }
}
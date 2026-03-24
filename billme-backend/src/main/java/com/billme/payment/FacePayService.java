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

    private static final double FACE_MATCH_THRESHOLD = 0.80;

    @Transactional
    public String payInvoice(Long invoiceId, Object paymentEmbedding) {

        // 🔥 Convert ANY format → double[]
        double[] embedding = FaceRecognitionUtil.parseEmbedding(paymentEmbedding);

        // 🔥 Strict validation AFTER parsing
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

        boolean match = FaceRecognitionUtil.isMatch(
                profile.getFaceEmbeddings(),
                embedding,
                FACE_MATCH_THRESHOLD
        );

        if (!match) {
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
}
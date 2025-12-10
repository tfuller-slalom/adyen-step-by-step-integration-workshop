package com.adyen.workshop.controllers;

import com.adyen.model.notification.NotificationRequest;
import com.adyen.model.notification.NotificationRequestItem;
import com.adyen.util.HMACValidator;
import com.adyen.workshop.configurations.ApplicationConfiguration;
import org.apache.coyote.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.adyen.workshop.services.TokenService;

import java.io.IOException;
import java.security.SignatureException;

/**
 * REST controller for receiving Adyen webhook notifications
 */
@RestController
public class WebhookController {
    private final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final ApplicationConfiguration applicationConfiguration;

    private final HMACValidator hmacValidator;

    private final TokenService tokenService;

    @Autowired
    public WebhookController(ApplicationConfiguration applicationConfiguration, HMACValidator hmacValidator, TokenService tokenService) {
        this.applicationConfiguration = applicationConfiguration;
        this.hmacValidator = hmacValidator;
        this.tokenService = tokenService;
    }

    // Step 16 - Validate the HMAC signature using the ADYEN_HMAC_KEY
    @PostMapping("/webhooks")
    public ResponseEntity<String> webhooks(@RequestBody String json) throws Exception {
        log.info("Received: {}", json);
        var notificationRequest = NotificationRequest.fromJson(json);
        var notificationRequestItem = notificationRequest.getNotificationItems().stream().findFirst();

        try {
            NotificationRequestItem item = notificationRequestItem.get();

            // Step 16 - Validate the HMAC signature using the ADYEN_HMAC_KEY
            if (!hmacValidator.validateHMAC(item, this.applicationConfiguration.getAdyenHmacKey())) {
                log.warn("Could not validate HMAC signature for incoming webhook message: {}", item);
                return ResponseEntity.unprocessableEntity().build();
            }

            switch (item.getEventCode()) {
                case "RECURRING_CONTRACT":
                    log.info("Received a recurring contract event");
                    var token = item.getAdditionalData().get("recurring.recurringDetailReference");
                    this.tokenService.setTokenId(token);
                    break;
                case "AUTHORISATION":
                    log.info("Payment was authorized!! {}", item.getPspReference());
                    break;
                case "AUTHORISATION_ADJUSTMENT":
                    log.info("Amount was adjusted!! {}", item.getPspReference());
                    break;
                case "CAPTURE":
                    log.info("Payment was captured!! {}", item.getPspReference());
                    break;
                case "CAPTURE_FAILED":
                    log.warn("Payment capture failed!! {}", item.getPspReference());
                    break;
                case "TECHNICAL_CANCEL":
                    log.info("Technical cancel was performed on payment!! {}", item.getPspReference());
                    break;
                case "CANCELLATION":
                    log.info("Payment was cancelled!! {}", item.getPspReference());
                    break;
                case "REFUND":
                    log.info("Payment was refunded!! {}", item.getPspReference());
                    break;
                case "REFUNDED_REVERSED":
                    log.warn("Payment refund was reversed!! {}", item.getPspReference());
                    break;
                case "REFUND_FAILED":
                    log.warn("Payment refund failed!! {}", item.getPspReference());
                    break;
                default:
                    log.info("Received webhook with event {}", item.toString());
            }

            return ResponseEntity.accepted().build();
        } catch (SignatureException e) {
            // Handle invalid signature
            return ResponseEntity.unprocessableEntity().build();
        } catch (Exception e) {
            // Handle all other errors
            return ResponseEntity.status(500).build();
        }
    }
}
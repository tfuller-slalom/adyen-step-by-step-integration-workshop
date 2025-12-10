package com.adyen.workshop.controllers;

import com.adyen.model.RequestOptions;
import com.adyen.model.checkout.*;
import com.adyen.workshop.configurations.ApplicationConfiguration;
import com.adyen.workshop.services.TokenService;
import com.adyen.service.checkout.RecurringApi;
import com.adyen.service.checkout.ModificationsApi;
import com.adyen.service.checkout.PaymentsApi;
import com.adyen.service.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for using the Adyen payments API.
 */
@RestController
public class ApiController {
    private final Logger log = LoggerFactory.getLogger(ApiController.class);

    private final ApplicationConfiguration applicationConfiguration;
    private final PaymentsApi paymentsApi;
    private final TokenService tokenService;
    private final RecurringApi recurringApi;
    private final ModificationsApi modificationsApi;

    public ApiController(ApplicationConfiguration applicationConfiguration, PaymentsApi paymentsApi, TokenService tokenService, RecurringApi recurringApi, ModificationsApi modificationsApi) {
        this.applicationConfiguration = applicationConfiguration;
        this.paymentsApi = paymentsApi;
        this.tokenService = tokenService;
        this.recurringApi = recurringApi;
        this.modificationsApi = modificationsApi;
    }

    // Step 0
    @GetMapping("/hello-world")
    public ResponseEntity<String> helloWorld() throws Exception {
        return ResponseEntity.ok().body("This is the 'Hello World' from the workshop - You've successfully finished step 0!");
    }

    // Step 7
    @PostMapping("/api/paymentMethods")
    public ResponseEntity<PaymentMethodsResponse> paymentMethods() throws IOException, ApiException {
        var req = new PaymentMethodsRequest()
            .merchantAccount(applicationConfiguration.getAdyenMerchantAccount());

        var idempotencyKey = UUID.randomUUID().toString();

        var res = paymentsApi.paymentMethods(req, new RequestOptions().idempotencyKey(idempotencyKey));

        return ResponseEntity.ok().body(res);
    }

    // Step 9 - Implement the /payments call to Adyen.
    @PostMapping("/api/payments")
    public ResponseEntity<PaymentResponse> payments(@RequestBody PaymentRequest body) throws IOException, ApiException {
        var orderId = UUID.randomUUID().toString();

        var req = new PaymentRequest()
            .reference(orderId)
            .amount(new Amount()
                .value(9998L)
                .currency("EUR"))
            .merchantAccount(applicationConfiguration.getAdyenMerchantAccount())
            .paymentMethod(body.getPaymentMethod())
            .channel(PaymentRequest.ChannelEnum.WEB)
            .returnUrl("https://animated-bassoon-p7ppr6q6j4vh7p7w-8080.app.github.dev/handleShopperRedirect")
            .origin("https://animated-bassoon-p7ppr6q6j4vh7p7w-8080.app.github.dev")
            .shopperIP("192.168.0.1")
            .shopperInteraction(PaymentRequest.ShopperInteractionEnum.ECOMMERCE)
            .browserInfo(body.getBrowserInfo())
            .billingAddress(new BillingAddress()
                .city("Amsterdam")
                .country("NL")
                .postalCode("1012KK")
                .street("Rokin")
                .houseNumberOrName("49"))
            .authenticationData(new AuthenticationData()
                .attemptAuthentication(AuthenticationData.AttemptAuthenticationEnum.ALWAYS));

        var idempotencyKey = UUID.randomUUID().toString();

        var res = paymentsApi.payments(req, new RequestOptions().idempotencyKey(idempotencyKey));

        return ResponseEntity.ok().body(res);
    }

    // Step 13 - Handle details call (triggered after Native 3DS2 flow)
    @PostMapping("/api/payments/details")
    public ResponseEntity<PaymentDetailsResponse> paymentsDetails(@RequestBody PaymentDetailsRequest detailsRequest) throws IOException, ApiException
    {

        return ResponseEntity.ok().body(null);
    }

    // Step 14 - Handle Redirect 3DS2 during payment.
    @GetMapping("/handleShopperRedirect")
    public RedirectView redirect(@RequestParam(required = false) String payload, @RequestParam(required = false) String redirectResult) throws IOException, ApiException {
        var paymentDetailsRequest = new PaymentDetailsRequest();

        PaymentCompletionDetails paymentCompletionDetails = new PaymentCompletionDetails();

        // Handle redirect result or payload
        if (redirectResult != null && !redirectResult.isEmpty()) {
            // For redirect, you are redirected to an Adyen domain to complete the 3DS2 challenge
            // After completing the 3DS2 challenge, you get the redirect result from Adyen in the returnUrl
            // We then pass on the redirectResult
            paymentCompletionDetails.redirectResult(redirectResult);
        } else if (payload != null && !payload.isEmpty()) {
            paymentCompletionDetails.payload(payload);
        }

        paymentDetailsRequest.setDetails(paymentCompletionDetails);

        var paymentsDetailsResponse = paymentsApi.paymentsDetails(paymentDetailsRequest);
        log.info("PaymentsDetailsResponse {}", paymentsDetailsResponse);

        // Handle response and redirect user accordingly
        var redirectURL = "https://animated-bassoon-p7ppr6q6j4vh7p7w-8080.app.github.dev/result/"; // Update your url here by replacing `http://localhost:8080` with where your application is hosted (if needed)
        switch (paymentsDetailsResponse.getResultCode()) {
            case AUTHORISED:
                redirectURL += "success";
                break;
            case PENDING:
            case RECEIVED:
                redirectURL += "pending";
                break;
            case REFUSED:
                redirectURL += "failed";
                break;
            default:
                redirectURL += "error";
                break;
        }
        return new RedirectView(redirectURL + "?reason=" + paymentsDetailsResponse.getResultCode());
    }

    @PostMapping("/api/subscription-create")
    public ResponseEntity<PaymentResponse> createSubscription(@RequestBody PaymentRequest body) throws IOException, ApiException {
        var orderId = UUID.randomUUID().toString();

        var shopperReference = UUID.randomUUID().toString();
        tokenService.setShopperReference(shopperReference);

        var req = new PaymentRequest()
            .reference(orderId)
            .amount(new Amount()
                .value(0L)
                .currency("EUR"))
            .storePaymentMethod(true)
            .recurringProcessingModel(PaymentRequest.RecurringProcessingModelEnum.SUBSCRIPTION)
            .shopperReference(shopperReference)
            .merchantAccount(applicationConfiguration.getAdyenMerchantAccount())
            .paymentMethod(body.getPaymentMethod())
            .channel(PaymentRequest.ChannelEnum.WEB)
            .returnUrl("https://animated-bassoon-p7ppr6q6j4vh7p7w-8080.app.github.dev/handleShopperRedirect");

        var idempotencyKey = UUID.randomUUID().toString();

        var res = paymentsApi.payments(req, new RequestOptions().idempotencyKey(idempotencyKey));

        return ResponseEntity.ok().body(res);
    }

    @PostMapping("/api/subscription-payment")
    public ResponseEntity<PaymentResponse> chargeSubscriptionPayment() throws IOException, ApiException {
        var orderId = UUID.randomUUID().toString();

        var tokenId = tokenService.getTokenId();

        var cardDetails = new CardDetails()
            .storedPaymentMethodId(tokenId)
            .type(CardDetails.TypeEnum.SCHEME);

        var req = new PaymentRequest()
            .reference(orderId)
            .amount(new Amount()
                .value(9998L)
                .currency("EUR"))
            .paymentMethod(new CheckoutPaymentMethod(cardDetails))
            //.storePaymentMethod(true)
            .recurringProcessingModel(PaymentRequest.RecurringProcessingModelEnum.SUBSCRIPTION)
            .shopperReference(tokenService.getShopperReference())
            .merchantAccount(applicationConfiguration.getAdyenMerchantAccount())
            .shopperInteraction(PaymentRequest.ShopperInteractionEnum.CONTAUTH);

        var idempotencyKey = UUID.randomUUID().toString();

        var res = paymentsApi.payments(req, new RequestOptions().idempotencyKey(idempotencyKey));

        return ResponseEntity.ok().body(res);
    }

    @PostMapping("/api/subscription-cancel")
    public ResponseEntity<PaymentResponse> cancelSubscription() throws IOException, ApiException {
        var tokenId = tokenService.getTokenId();
        var shopperReference = tokenService.getShopperReference();
        var merchantAccount = applicationConfiguration.getAdyenMerchantAccount();

        recurringApi.deleteTokenForStoredPaymentDetails(tokenId, shopperReference, merchantAccount);

        return ResponseEntity.ok().body(null);
    }

    @PostMapping("/api/preauthorisation")
    public ResponseEntity<PaymentResponse> preauth(@RequestBody PaymentRequest body) throws IOException, ApiException {
        var orderId = UUID.randomUUID().toString();

        var shopperReference = UUID.randomUUID().toString();
        tokenService.setShopperReference(shopperReference);

        Map<String, String> additionalData = new HashMap<>();
        additionalData.put("authorisationType", "PreAuth"); 

        var req = new PaymentRequest()
            .reference(orderId)
            .amount(new Amount()
                .value(1000L)
                .currency("EUR"))
            .shopperReference(shopperReference)
            .merchantAccount(applicationConfiguration.getAdyenMerchantAccount())
            .paymentMethod(body.getPaymentMethod())
            .channel(PaymentRequest.ChannelEnum.WEB)
            .returnUrl("https://animated-bassoon-p7ppr6q6j4vh7p7w-8080.app.github.dev/handleShopperRedirect")
            .additionalData(additionalData);

        var idempotencyKey = UUID.randomUUID().toString();

        var res = paymentsApi.payments(req, new RequestOptions().idempotencyKey(idempotencyKey));

        return ResponseEntity.ok().body(res);
    }

    @PostMapping("/api/modify-amount/{pspReference}")
    public ResponseEntity<PaymentAmountUpdateResponse> modifyAmount(@PathVariable String pspReference) throws IOException, ApiException {
        var orderId = UUID.randomUUID().toString();

        var req = new PaymentAmountUpdateRequest()
            .industryUsage(PaymentAmountUpdateRequest.IndustryUsageEnum.DELAYEDCHARGE)
            .reference(orderId)
            .amount(new Amount()
                .value(6600L)
                .currency("EUR"))
            .merchantAccount(applicationConfiguration.getAdyenMerchantAccount());

        var idempotencyKey = UUID.randomUUID().toString();

        var res = modificationsApi.updateAuthorisedAmount(pspReference, req, new RequestOptions().idempotencyKey(idempotencyKey));

        return ResponseEntity.ok().body(res);
    }

    @PostMapping("/api/capture/{pspReference}")
    public ResponseEntity<PaymentCaptureResponse> capture(@PathVariable String pspReference) throws IOException, ApiException {
        var orderId = UUID.randomUUID().toString();

        var req = new PaymentCaptureRequest()
            .reference(orderId)
            .amount(new Amount()
                .currency("EUR")
                .value(6600L))
            .merchantAccount(applicationConfiguration.getAdyenMerchantAccount());

        var idempotencyKey = UUID.randomUUID().toString();

        var res = modificationsApi.captureAuthorisedPayment(pspReference, req, new RequestOptions().idempotencyKey(idempotencyKey));

        return ResponseEntity.ok().body(res);
    }

    @PostMapping("/api/cancel/{pspReference}")
    public ResponseEntity<PaymentCancelResponse> cancel(@PathVariable String pspReference) throws IOException, ApiException {
        var orderId = UUID.randomUUID().toString();

        var req = new PaymentCancelRequest()
            .reference(orderId)
            .merchantAccount(applicationConfiguration.getAdyenMerchantAccount());

        var idempotencyKey = UUID.randomUUID().toString();

        var res = modificationsApi.cancelAuthorisedPaymentByPspReference(pspReference, req, new RequestOptions().idempotencyKey(idempotencyKey));

        return ResponseEntity.ok().body(res);
    }

    @PostMapping("/api/refund/{pspReference}")
    public ResponseEntity<PaymentRefundResponse> refund(@PathVariable String pspReference) throws IOException, ApiException {
        var orderId = UUID.randomUUID().toString();

        var req = new PaymentRefundRequest()
            .reference(orderId)
            .amount(new Amount()
                .currency("EUR")
                .value(6600L))
            .merchantAccount(applicationConfiguration.getAdyenMerchantAccount());

        var idempotencyKey = UUID.randomUUID().toString();

        var res = modificationsApi.refundCapturedPayment(pspReference, req, new RequestOptions().idempotencyKey(idempotencyKey));

        return ResponseEntity.ok().body(res);
    }
}

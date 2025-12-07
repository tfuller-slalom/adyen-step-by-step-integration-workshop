const clientKey = document.getElementById("clientKey").innerHTML;
const { AdyenCheckout, Dropin } = window.AdyenWeb;

// Starts the (Adyen.Web) AdyenCheckout with your specified configuration by calling the `/paymentMethods` endpoint.
async function startCheckout() {
    try {
        // Step 8 - Retrieve the available payment methods
        const paymentMethodsResponse = await fetch("/api/paymentMethods", {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
            }
        }).then(response => response.json());

        const configuration = {
            paymentMethodsResponse: paymentMethodsResponse,
            clientKey,
            locale: "en_US",
            countryCode: 'NL',
            environment: "test",
            showPayButton: true,
            translations: {
                'en-US': {
                    'creditCard.securityCode.label': 'CVV/CVC'
                }
            },
            onSubmit: async (state, component, actions) => {
                console.log("⚠️ onSubmit")
                if (!state.isValid) {
                    console.warn("Payment form submission invalid");
                    actions.reject();
                }

                try {
                    const req = JSON.stringify(state.data);
                    const { action, order, resultCode } = await createSubscription(req);
                    
                    if (!resultCode) {
                        console.warn("Unknown result code");
                        actions.reject();
                    }

                    actions.resolve({
                        resultCode,
                        action,
                        order
                    })
                } catch (error) {
                    console.error(error);
                    actions.reject();
                }
            },
            onAdditionalDetails: (state, component, actions) => {
                console.log("⚠️ onAdditionalDetails")
                console.log(state);
            },
            onPaymentCompleted: (result, component) => {
                console.log("⚠️ onPaymentCompleted")
                handleOnPaymentCompleted(result)
            },
            onPaymentFailed: (result, component) => {
                console.log("⚠️ onPaymentFailed")
                handleOnPaymentFailed(result)
            },
            onError: (error) => {
                console.log("⚠️ onError")
                console.error(error);
                window.location.href = "/result/error"
            },
        };

        const paymentMethodsConfiguration = {
            card: {
                showBrandIcon: true,
                hasHolderName: true,
                holderNameRequired: true,
                name: "Credit or debit card",
                amount: {
                    value: 9998,
                    currency: "EUR",
                },
                placeholders: {
                    cardNumber: '1234 5678 9012 3456',
                    expiryDate: 'MM/YY',
                    securityCodeThreeDigits: '123',
                    securityCodeFourDigits: '1234',
                    holderName: 'Developer Relations Team'
                }
            }
        };

        // Start the AdyenCheckout and mount the element onto the `payment`-div.
        const adyenCheckout = await AdyenCheckout(configuration);
        const dropin = new Dropin(adyenCheckout, { 
            paymentMethodsConfiguration: paymentMethodsConfiguration 
        });
        
        dropin.mount(document.getElementById("payment"));
    } catch (error) {
        console.error(error);
        alert("Error occurred. Look at console for details.");
    }
}

function createPayment(req) {
    return fetch("/api/payments", {
            method: "POST",
            body: req,
            headers: {
                "Content-Type": "application/json",
            }
        }).then(response => response.json());
}

function createSubscription(req) {
    return fetch("/api/subscription-create", {
            method: "POST",
            body: req,
            headers: {
                "Content-Type": "application/json",
            }
        }).then(response => response.json());
}

// Step 10 - Function to handle payment completion redirects
function handleOnPaymentCompleted(response) {
    switch (response.resultCode) {
        case "Authorised":
            window.location.href = "/result/success";
            break;
        case "Pending":
        case "Received":
            window.location.href = "/result/pending";
            break;
        default:
            window.location.href = "/result/error";
            break;
    }
}

// Step 10 - Function to handle payment failure redirects
function handleOnPaymentFailed(response) {
    switch (response.resultCode) {
        case "Cancelled":
        case "Refused":
            window.location.href = "/result/failed";
            break;
        default:
            window.location.href = "/result/error";
            break;
    }
}

startCheckout();
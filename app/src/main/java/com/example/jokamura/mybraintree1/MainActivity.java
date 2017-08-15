package com.example.jokamura.mybraintree1;

import android.os.Bundle;
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.app.Activity;
import android.view.View;
import android.widget.Button;

import com.loopj.android.http.*;

import cz.msebera.android.httpclient.Header;

import com.braintreepayments.api.BraintreeFragment;
import com.braintreepayments.api.DataCollector;

import com.braintreepayments.api.models.PaymentMethodNonce;

import com.braintreepayments.api.exceptions.InvalidArgumentException;
import com.braintreepayments.api.exceptions.ErrorWithResponse;
import com.braintreepayments.api.exceptions.BraintreeError;

import com.braintreepayments.api.dropin.DropInRequest;
import com.braintreepayments.api.dropin.DropInResult;
import com.braintreepayments.api.dropin.DropInActivity;
import com.braintreepayments.api.PayPal;
import com.braintreepayments.api.models.PayPalRequest;
import com.braintreepayments.api.models.PayPalAccountNonce;
import com.braintreepayments.api.models.PostalAddress;

import com.braintreepayments.api.interfaces.PaymentMethodNonceCreatedListener;
import com.braintreepayments.api.interfaces.BraintreeCancelListener;
import com.braintreepayments.api.interfaces.BraintreeErrorListener;
import com.braintreepayments.api.interfaces.BraintreeResponseListener;


public class MainActivity extends AppCompatActivity implements PaymentMethodNonceCreatedListener, BraintreeCancelListener, BraintreeErrorListener {

    static final int REQUEST_CODE = 1000;

    private String clientToken;

    private BraintreeFragment mBraintreeFragment;

    private String nonce;

    private Boolean isVault = false;

    private void createBT(String clientToken) {
        this.clientToken = clientToken;
        try {
            this.mBraintreeFragment = BraintreeFragment.newInstance(this, clientToken);
            // mBraintreeFragment is ready to use!
        } catch (InvalidArgumentException e) {
            // There was an issue with your authorization string.
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Rendering custom button
        Button button = (Button)findViewById(R.id.button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setupBraintreeAndStartExpressCheckout();
            }
        });
        Button button2 = (Button)findViewById(R.id.button2);
        button2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startBillingAgreement();
            }
        });


        AsyncHttpClient client = new AsyncHttpClient();

        client.get("https://jo-pp-ruby-demo.herokuapp.com/brain/get_token", new TextHttpResponseHandler() {
            @Override
            public void onSuccess(int statusCode, Header[] headers, String clientToken) {
                createBT(clientToken);
            }
            @Override
            public void onFailure(int statusCode, Header[] headers, String responseString, Throwable
                    error) {
                System.out.println("Error! " + responseString);

            }

        });
    }

    @Override
    public void onResume(){
        super.onResume();
        // === Rendering DropnIn UI ===
        onBraintreeSubmit();
    }

    // Checkout by custom button
    public void setupBraintreeAndStartExpressCheckout() {
        this.isVault = false;
        PayPalRequest request = new PayPalRequest("12345")
                .currencyCode("JPY");
        PayPal.requestOneTimePayment(this.mBraintreeFragment, request);
    }

    // Vault by custom button
    public void startBillingAgreement() {
        this.isVault = true;
        PayPalRequest request = new PayPalRequest()
                .localeCode("JP")
                .billingAgreementDescription("BO Agreement!!!");
        PayPal.requestBillingAgreement(this.mBraintreeFragment, request);
    }

    // Called by custom button checkout
    @Override
    public void onPaymentMethodNonceCreated(PaymentMethodNonce paymentMethodNonce) {
        // Send nonce to server
        this.nonce = paymentMethodNonce.getNonce();
        if (paymentMethodNonce instanceof PayPalAccountNonce) {
            PayPalAccountNonce payPalAccountNonce = (PayPalAccountNonce)paymentMethodNonce;

            // Access additional information
            String email = payPalAccountNonce.getEmail();
            String firstName = payPalAccountNonce.getFirstName();
            String lastName = payPalAccountNonce.getLastName();
            String phone = payPalAccountNonce.getPhone();

            // See PostalAddress.java for details
            PostalAddress billingAddress = payPalAccountNonce.getBillingAddress();
            PostalAddress shippingAddress = payPalAccountNonce.getShippingAddress();

            DataCollector.collectDeviceData(this.mBraintreeFragment, new BraintreeResponseListener<String>() {
               @Override
               public void onResponse(String deviceData) {
                    // send deviceData to your server
                   if (!isVault) {
                       postNonceToServer("12345", "JPY", deviceData);
                   } else  {
                       postNonceToServerVault(deviceData);
                   }
               }
            });

        }
    }

    // Called by custome button checkout
    @Override
    public void onCancel(int requestCode) {
        // Use this to handle a canceled activity, if the given requestCode is important.
        // You may want to use this callback to hide loading indicators, and prepare your UI for input
    }

    // Called by custome button checkout
    @Override
    public void onError(Exception error) {
        if (error instanceof ErrorWithResponse) {
            ErrorWithResponse errorWithResponse = (ErrorWithResponse) error;
            BraintreeError cardErrors = ((ErrorWithResponse) error).errorFor("creditCard");
            if (cardErrors != null) {
                // There is an issue with the credit card.
                BraintreeError expirationMonthError = cardErrors.errorFor("expirationMonth");
                if (expirationMonthError != null) {
                    // There is an issue with the expiration month.
                    System.out.println(expirationMonthError.getMessage());
                }
            }
        }
    }

    //Call this for rendering DropIn UI
    public void onBraintreeSubmit() {
        DropInRequest dropInRequest = new DropInRequest()
                .clientToken(this.clientToken);
        dropInRequest.collectDeviceData(true); // For Vault
        startActivityForResult(dropInRequest.getIntent(this), REQUEST_CODE);
    }

    // This is called after DropIn UI closed.
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                DropInResult result = data.getParcelableExtra(DropInResult.EXTRA_DROP_IN_RESULT);
                // use the result to update your UI and send the payment method nonce to your server
                // === Send the nonce to finish rhe transaction ===
                this.nonce = result.getPaymentMethodNonce().getNonce();
                postNonceToServer("22345", "JPY", result.getDeviceData());
            } else if (resultCode == Activity.RESULT_CANCELED) {
                // the user canceled
            } else {
                // handle errors here, an exception may be available in
                Exception error = (Exception) data.getSerializableExtra(DropInActivity.EXTRA_ERROR);
            }
        }
    }

    // Checkout
    void postNonceToServer(String amount, String currency, String deviceData) {
        AsyncHttpClient client = new AsyncHttpClient();
        RequestParams params = new RequestParams();
        params.put("payment_method_nonce", this.nonce);
        params.put("amount", amount);
        params.put("currency", currency);
        params.put("device_data", deviceData);
        client.post("https://jo-pp-ruby-demo.herokuapp.com/brain/checkout_ec", params,
                new AsyncHttpResponseHandler() {
                    // Your implementation here
                    @Override
                    public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {
                        // Successfully got a response
                        System.out.println("Success! " + new String(responseBody));
                    }
                    @Override
                    public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable
                            error) {
                        System.out.println("Error! " + new String(responseBody));

                    }
                }
        );
    }

    //Vault
    void postNonceToServerVault(String deviceData) {
        AsyncHttpClient client = new AsyncHttpClient();
        RequestParams params = new RequestParams();
        params.put("payment_method_nonce", this.nonce);
        params.put("device_data", deviceData);
        client.post("https://jo-pp-ruby-demo.herokuapp.com/brain/create_cs", params,
                new AsyncHttpResponseHandler() {
                    // Your implementation here
                    @Override
                    public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {
                        // Successfully got a response
                        System.out.println("Success! " + new String(responseBody));
                    }
                    @Override
                    public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable
                            error) {
                        System.out.println("Error! " + new String(responseBody));

                    }
                }
        );
    }
}

package com.adyen.workshop.services;

import java.util.HashMap;
import java.util.Map;

public class TokenService {
    private String shopperReference;
    private String tokenId;

    public TokenService() {
    }

    public String getTokenId() {
        return this.tokenId;
    }

    public void setTokenId(String tokenId) {
        this.tokenId = tokenId;
    }

    public String getShopperReference() {
        return this.shopperReference;
    }

    public void setShopperReference(String shopperReference) {
        this.shopperReference = shopperReference;
    }
}

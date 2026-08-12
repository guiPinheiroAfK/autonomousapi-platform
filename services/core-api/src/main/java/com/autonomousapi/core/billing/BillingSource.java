package com.autonomousapi.core.billing;

/** Canal de cobrança (spec 03) — modelado para múltiplos desde já, mesmo só web_stripe ativo hoje. */
public enum BillingSource {
    WEB_STRIPE,
    IOS_IAP,
    ANDROID_IAP
}

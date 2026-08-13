package com.autonomousapi.core.affiliate.dto;

import com.autonomousapi.core.affiliate.AffiliatePartner;
import java.util.UUID;

public record AffiliatePartnerResponse(UUID id, String name, String category) {

    public static AffiliatePartnerResponse from(AffiliatePartner p) {
        return new AffiliatePartnerResponse(p.getId(), p.getName(), p.getCategory());
    }
}

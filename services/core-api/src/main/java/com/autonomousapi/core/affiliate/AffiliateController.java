package com.autonomousapi.core.affiliate;

import com.autonomousapi.core.affiliate.dto.AffiliateClickRequest;
import com.autonomousapi.core.affiliate.dto.AffiliateClickResponse;
import com.autonomousapi.core.affiliate.dto.AffiliatePartnerResponse;
import com.autonomousapi.core.security.jwt.JwtPrincipal;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Afiliados (spec 06, item 4). Qualquer usuário autenticado do tenant pode ver e clicar. */
@RestController
@RequestMapping("/v1/affiliates")
public class AffiliateController {

    private final AffiliateService affiliateService;

    public AffiliateController(AffiliateService affiliateService) {
        this.affiliateService = affiliateService;
    }

    @GetMapping("/partners")
    public List<AffiliatePartnerResponse> listPartners() {
        return affiliateService.listPartners();
    }

    @PostMapping("/partners/{partnerId}/click")
    public AffiliateClickResponse click(
            @PathVariable UUID partnerId, @RequestBody(required = false) AffiliateClickRequest req,
            Authentication auth) {
        return affiliateService.click(
                principal(auth), partnerId, req != null ? req : new AffiliateClickRequest(null));
    }

    private JwtPrincipal principal(Authentication auth) {
        return (JwtPrincipal) auth.getPrincipal();
    }
}

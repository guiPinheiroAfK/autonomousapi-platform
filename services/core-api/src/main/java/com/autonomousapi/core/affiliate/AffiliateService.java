package com.autonomousapi.core.affiliate;

import com.autonomousapi.core.affiliate.dto.AffiliateClickRequest;
import com.autonomousapi.core.affiliate.dto.AffiliateClickResponse;
import com.autonomousapi.core.affiliate.dto.AffiliatePartnerResponse;
import com.autonomousapi.core.error.NotFoundException;
import com.autonomousapi.core.security.jwt.JwtPrincipal;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Afiliados (spec 06, item 4). Catálogo é global; clique é escopado por tenant/usuário. */
@Service
public class AffiliateService {

    private final AffiliatePartnerRepository partners;
    private final AffiliateClickRepository clicks;

    public AffiliateService(AffiliatePartnerRepository partners, AffiliateClickRepository clicks) {
        this.partners = partners;
        this.clicks = clicks;
    }

    @Transactional(readOnly = true)
    public List<AffiliatePartnerResponse> listPartners() {
        return partners.findAllByOrderByNameAsc().stream().map(AffiliatePartnerResponse::from).toList();
    }

    /** Registra o clique (métrica) e devolve o link para o front redirecionar. */
    @Transactional
    public AffiliateClickResponse click(JwtPrincipal principal, UUID partnerId, AffiliateClickRequest req) {
        AffiliatePartner partner = partners.findById(partnerId)
                .orElseThrow(() -> new NotFoundException("Parceiro não encontrado."));
        clicks.save(new AffiliateClick(principal.tenantId(), partner.getId(), req.vehicleId(), principal.userId()));
        return new AffiliateClickResponse(partner.getLinkBase());
    }
}

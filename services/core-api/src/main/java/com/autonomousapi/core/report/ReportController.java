package com.autonomousapi.core.report;

import com.autonomousapi.core.security.jwt.JwtPrincipal;
import com.autonomousapi.core.vehicle.cost.VehicleCostService;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Export de relatório (spec 05, Fase 1: painel do gestor). Leitura: qualquer usuário do tenant. */
@RestController
@RequestMapping("/v1/reports")
public class ReportController {

    private final VehicleCostService costService;

    public ReportController(VehicleCostService costService) {
        this.costService = costService;
    }

    @GetMapping(value = "/costs.csv", produces = "text/csv")
    public ResponseEntity<byte[]> costsCsv(Authentication auth) {
        JwtPrincipal principal = (JwtPrincipal) auth.getPrincipal();
        byte[] body = costService.exportCsv(principal).getBytes(StandardCharsets.UTF_8);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(
                ContentDisposition.attachment().filename("relatorio-custos.csv", StandardCharsets.UTF_8).build());

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(body);
    }
}

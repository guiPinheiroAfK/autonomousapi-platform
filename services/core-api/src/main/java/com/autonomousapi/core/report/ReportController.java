package com.autonomousapi.core.report;

import com.autonomousapi.core.expense.ExpenseEntryService;
import com.autonomousapi.core.security.jwt.JwtPrincipal;
import com.autonomousapi.core.workorder.WorkOrderService;
import com.autonomousapi.core.workorder.dto.WorkOrderReportResponse;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Relatórios (spec 05, Fase 1: painel do gestor). Leitura: qualquer usuário do tenant. */
@RestController
@RequestMapping("/v1/reports")
public class ReportController {

    private final ExpenseEntryService expenseService;
    private final WorkOrderService workOrderService;

    public ReportController(ExpenseEntryService expenseService, WorkOrderService workOrderService) {
        this.expenseService = expenseService;
        this.workOrderService = workOrderService;
    }

    @GetMapping(value = "/costs.csv", produces = "text/csv")
    public ResponseEntity<byte[]> costsCsv(Authentication auth) {
        JwtPrincipal principal = (JwtPrincipal) auth.getPrincipal();
        byte[] body = expenseService.exportCsv(principal).getBytes(StandardCharsets.UTF_8);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(
                ContentDisposition.attachment().filename("relatorio-custos.csv", StandardCharsets.UTF_8).build());

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(body);
    }

    /** Custo de manutenção por tipo (12 meses) + ranking de veículo, a partir de Ordem de Serviço real. */
    @GetMapping("/maintenance-summary")
    public WorkOrderReportResponse maintenanceSummary(Authentication auth) {
        return workOrderService.maintenanceSummary((JwtPrincipal) auth.getPrincipal());
    }
}

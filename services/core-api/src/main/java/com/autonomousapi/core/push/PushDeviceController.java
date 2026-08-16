package com.autonomousapi.core.push;

import com.autonomousapi.core.push.dto.RegisterDeviceRequest;
import com.autonomousapi.core.security.jwt.JwtPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Registro de device token — qualquer usuário autenticado (gestor ou motorista, ADR 0016). */
@RestController
@RequestMapping("/v1/push/devices")
public class PushDeviceController {

    private final PushNotificationService pushNotificationService;

    public PushDeviceController(PushNotificationService pushNotificationService) {
        this.pushNotificationService = pushNotificationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void register(@Valid @RequestBody RegisterDeviceRequest req, Authentication auth) {
        JwtPrincipal principal = (JwtPrincipal) auth.getPrincipal();
        pushNotificationService.registerDevice(principal.userId(), req.token(), req.plataforma());
    }
}

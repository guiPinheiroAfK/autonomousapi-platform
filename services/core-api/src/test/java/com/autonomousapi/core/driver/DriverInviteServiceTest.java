package com.autonomousapi.core.driver;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.autonomousapi.core.email.EmailSender;
import com.autonomousapi.core.error.DriverEmailRequiredException;
import com.autonomousapi.core.error.EmailAlreadyUsedException;
import com.autonomousapi.core.error.InvalidDriverInviteTokenException;
import com.autonomousapi.core.security.jwt.JwtPrincipal;
import com.autonomousapi.core.user.User;
import com.autonomousapi.core.user.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class DriverInviteServiceTest {

    private final DriverRepository drivers = mock(DriverRepository.class);
    private final DriverInviteRepository invites = mock(DriverInviteRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final EmailSender emailSender = mock(EmailSender.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

    private final DriverInviteService service = new DriverInviteService(
            drivers, invites, users, emailSender, passwordEncoder, 72, "http://localhost:5180");

    private final UUID tenantId = UUID.randomUUID();
    private final JwtPrincipal principal = new JwtPrincipal(UUID.randomUUID(), tenantId, "GESTOR_FROTA");

    @Test
    void inviteExigeEmailNoMotorista() {
        Driver semEmail = new Driver(tenantId, "João", "12345678901", null);
        UUID id = semEmail.getId();
        when(drivers.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.of(semEmail));

        assertThrows(DriverEmailRequiredException.class, () -> service.invite(principal, id));
        verify(emailSender, never()).sendDriverInviteEmail(anyString(), anyString());
    }

    @Test
    void inviteRejeitaEmailJaUsadoPorConta() {
        Driver comEmail = new Driver(tenantId, "João", "12345678901", null);
        comEmail.update("João", "12345678901", null, DriverStatus.ATIVO, null, "joao@frota.com");
        UUID id = comEmail.getId();
        when(drivers.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.of(comEmail));
        when(users.existsByEmail("joao@frota.com")).thenReturn(true);

        assertThrows(EmailAlreadyUsedException.class, () -> service.invite(principal, id));
        verify(invites, never()).save(any());
    }

    @Test
    void inviteEnviaEmailComTokenESalvaConvite() {
        Driver comEmail = new Driver(tenantId, "João", "12345678901", null);
        comEmail.update("João", "12345678901", null, DriverStatus.ATIVO, null, "joao@frota.com");
        UUID id = comEmail.getId();
        when(drivers.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.of(comEmail));
        when(users.existsByEmail("joao@frota.com")).thenReturn(false);
        when(invites.findAllByDriverIdAndUsedAtIsNull(id)).thenReturn(List.of());

        service.invite(principal, id);

        verify(invites).save(any(DriverInvite.class));
        verify(emailSender).sendDriverInviteEmail(eq("joao@frota.com"), anyString());
    }

    @Test
    void acceptRejeitaTokenInexistente() {
        when(invites.findByTokenHash(anyString())).thenReturn(Optional.empty());
        assertThrows(InvalidDriverInviteTokenException.class, () -> service.accept("qualquer", "senha1234"));
    }

    @Test
    void acceptRejeitaTokenExpirado() {
        DriverInvite expirado = new DriverInvite(UUID.randomUUID(), "hash", Instant.now().minusSeconds(10));
        when(invites.findByTokenHash(anyString())).thenReturn(Optional.of(expirado));
        assertThrows(InvalidDriverInviteTokenException.class, () -> service.accept("tok", "senha1234"));
        verify(users, never()).save(any());
    }

    @Test
    void acceptCriaLoginMotoristaEVinculaAoDriver() {
        Driver driver = new Driver(tenantId, "João", "12345678901", null);
        driver.update("João", "12345678901", null, DriverStatus.ATIVO, null, "joao@frota.com");
        DriverInvite invite = new DriverInvite(driver.getId(), "hash", Instant.now().plusSeconds(3600));
        when(invites.findByTokenHash(anyString())).thenReturn(Optional.of(invite));
        when(drivers.findById(driver.getId())).thenReturn(Optional.of(driver));
        when(users.existsByEmail("joao@frota.com")).thenReturn(false);
        when(passwordEncoder.encode("senha1234")).thenReturn("hashed");

        service.accept("tok", "senha1234");

        // cria a conta MOTORISTA no tenant do motorista, com o e-mail do driver como login
        verify(users).save(any(User.class));
        // vincula o registro operacional à conta criada e consome o convite
        assertTrue(driver.hasLogin());
        assertTrue(invite.getUsedAt() != null);
    }
}

package com.autonomousapi.core.error;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EmailAlreadyUsedException.class)
    public ResponseEntity<ApiError> handleEmailUsed(EmailAlreadyUsedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError("email_already_used", ex.getMessage()));
    }

    @ExceptionHandler(PlateAlreadyUsedException.class)
    public ResponseEntity<ApiError> handlePlateUsed(PlateAlreadyUsedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError("plate_already_used", ex.getMessage()));
    }

    @ExceptionHandler(CnhAlreadyUsedException.class)
    public ResponseEntity<ApiError> handleCnhUsed(CnhAlreadyUsedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError("cnh_already_used", ex.getMessage()));
    }

    @ExceptionHandler(VehicleAlreadyAssignedException.class)
    public ResponseEntity<ApiError> handleVehicleAssigned(VehicleAlreadyAssignedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError("vehicle_already_assigned", ex.getMessage()));
    }

    @ExceptionHandler(DriverEmailRequiredException.class)
    public ResponseEntity<ApiError> handleDriverEmailRequired(DriverEmailRequiredException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError("driver_email_required", ex.getMessage()));
    }

    @ExceptionHandler(InvalidDriverInviteTokenException.class)
    public ResponseEntity<ApiError> handleInvalidDriverInvite(InvalidDriverInviteTokenException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError("invalid_driver_invite_token", ex.getMessage()));
    }

    @ExceptionHandler(DriverWithoutLoginException.class)
    public ResponseEntity<ApiError> handleDriverWithoutLogin(DriverWithoutLoginException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError("driver_without_login", ex.getMessage()));
    }

    @ExceptionHandler(InvalidTeamInviteTokenException.class)
    public ResponseEntity<ApiError> handleInvalidTeamInvite(InvalidTeamInviteTokenException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError("invalid_team_invite_token", ex.getMessage()));
    }

    @ExceptionHandler(InvalidTeamRoleException.class)
    public ResponseEntity<ApiError> handleInvalidTeamRole(InvalidTeamRoleException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError("invalid_team_role", ex.getMessage()));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiError> handleInvalidCredentials(InvalidCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiError("invalid_credentials", ex.getMessage()));
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<ApiError> handleInvalidRefresh(InvalidRefreshTokenException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiError("invalid_refresh_token", ex.getMessage()));
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError("not_found", ex.getMessage()));
    }

    @ExceptionHandler(BillingNotConfiguredException.class)
    public ResponseEntity<ApiError> handleBillingNotConfigured(BillingNotConfiguredException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiError("billing_not_configured", ex.getMessage()));
    }

    @ExceptionHandler(GoogleAuthNotConfiguredException.class)
    public ResponseEntity<ApiError> handleGoogleAuthNotConfigured(GoogleAuthNotConfiguredException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiError("google_auth_not_configured", ex.getMessage()));
    }

    @ExceptionHandler(SubscriptionRequiredException.class)
    public ResponseEntity<ApiError> handleSubscriptionRequired(SubscriptionRequiredException ex) {
        return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
                .body(new ApiError("subscription_required", ex.getMessage()));
    }

    @ExceptionHandler(InvalidVerificationTokenException.class)
    public ResponseEntity<ApiError> handleInvalidVerificationToken(InvalidVerificationTokenException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError("invalid_verification_token", ex.getMessage()));
    }

    @ExceptionHandler(InvalidPasswordResetTokenException.class)
    public ResponseEntity<ApiError> handleInvalidPasswordResetToken(InvalidPasswordResetTokenException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError("invalid_password_reset_token", ex.getMessage()));
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ApiError> handleTooManyRequests(TooManyRequestsException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new ApiError("too_many_requests", ex.getMessage()));
    }

    @ExceptionHandler(TripStateConflictException.class)
    public ResponseEntity<ApiError> handleTripInProgress(TripStateConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError("trip_already_in_progress", ex.getMessage()));
    }

    @ExceptionHandler(RoutePlanAlreadyAssignedException.class)
    public ResponseEntity<ApiError> handleRoutePlanAssigned(RoutePlanAlreadyAssignedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError("route_plan_already_assigned", ex.getMessage()));
    }

    @ExceptionHandler(RoutePlanInvalidException.class)
    public ResponseEntity<ApiError> handleRoutePlanInvalid(RoutePlanInvalidException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError("route_plan_invalid", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .orElse("Requisição inválida.");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError("validation_error", detail));
    }
}

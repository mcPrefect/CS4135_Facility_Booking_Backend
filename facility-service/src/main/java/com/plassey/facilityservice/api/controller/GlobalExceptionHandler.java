package com.plassey.facilityservice.api.controller;

import com.plassey.facilityservice.application.service.FacilityApplicationService.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(FacilityNotFoundException.class)
    public ProblemDetail handleNotFound(FacilityNotFoundException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setType(URI.create("https://plassey.planner/errors/facility-not-found"));
        pd.setProperty("timestamp", Instant.now().toString());
        return pd;
    }

    @ExceptionHandler(FacilityNameConflictException.class)
    public ProblemDetail handleConflict(FacilityNameConflictException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setType(URI.create("https://plassey.planner/errors/facility-name-conflict"));
        pd.setProperty("timestamp", Instant.now().toString());
        return pd;
    }

    @ExceptionHandler({InvalidFacilityTypeException.class, InvalidFacilityStatusException.class,
                        IllegalArgumentException.class, IllegalStateException.class})
    public ProblemDetail handleBadRequest(RuntimeException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        pd.setType(URI.create("https://plassey.planner/errors/bad-request"));
        pd.setProperty("timestamp", Instant.now().toString());
        return pd;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        pd.setType(URI.create("https://plassey.planner/errors/internal"));
        pd.setProperty("timestamp", Instant.now().toString());
        return pd;
    }
}

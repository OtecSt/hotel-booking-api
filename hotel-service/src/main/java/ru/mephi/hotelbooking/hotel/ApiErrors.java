package ru.mephi.hotelbooking.hotel;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class ApiErrors {

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail constraintViolation() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "The operation conflicts with existing data");
    }
}

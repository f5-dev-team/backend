package com.f5.scholarship_backend.global.exception;

public record FieldErrorResponse(String field, String reason) {
}
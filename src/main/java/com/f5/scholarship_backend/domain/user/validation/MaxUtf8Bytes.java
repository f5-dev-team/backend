package com.f5.scholarship_backend.domain.user.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = MaxUtf8BytesValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface MaxUtf8Bytes {
	String message() default "입력값은 UTF-8 기준 {max}바이트 이하여야 합니다.";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

	int max();
}

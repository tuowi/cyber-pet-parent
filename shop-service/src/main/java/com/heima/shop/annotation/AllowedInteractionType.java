package com.heima.shop.annotation;

import com.heima.shop.validator.AllowedTypeValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = AllowedTypeValidator.class)
public @interface AllowedInteractionType {
    String message() default "effectType 必须为 FEED、PET 或 BATHE 之一";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
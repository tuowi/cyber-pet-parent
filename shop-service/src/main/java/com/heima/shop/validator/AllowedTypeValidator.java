package com.heima.shop.validator;

import com.heima.api.enums.InteractionType;
import com.heima.shop.annotation.AllowedInteractionType;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.EnumSet;
import java.util.Set;

public class AllowedTypeValidator implements ConstraintValidator<AllowedInteractionType, InteractionType> {

    private final Set<InteractionType> allowed = EnumSet.of(
            InteractionType.FEED,
            InteractionType.PET,
            InteractionType.BATHE
    );

    @Override
    public boolean isValid(InteractionType value, ConstraintValidatorContext context) {
        // 如果允许为 null，去掉下面的 null 判断；如果禁止 null，加上 @NotNull 一起用
        return value == null || allowed.contains(value);
    }
}

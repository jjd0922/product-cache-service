package com.product.presentation.dto.request;

import jakarta.validation.constraints.Min;

import java.util.List;

public record IdsRequest(
        List<@Min(value = 1, message = "ids의 각 값은 1 이상의 값이어야 합니다.") Long> ids
) {
}
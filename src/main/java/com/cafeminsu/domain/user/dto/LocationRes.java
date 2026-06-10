package com.cafeminsu.domain.user.dto;

import java.math.BigDecimal;

public record LocationRes(
        BigDecimal latitude,
        BigDecimal longitude
) {
}

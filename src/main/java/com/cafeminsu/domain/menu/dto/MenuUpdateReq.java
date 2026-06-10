package com.cafeminsu.domain.menu.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record MenuUpdateReq(
        @Size(max = 100) String name,
        @Size(max = 500) String description,
        @Min(value = 0) Integer price,
        @Size(max = 50) String category,
        @Size(max = 500) String imageUrl
) {
}

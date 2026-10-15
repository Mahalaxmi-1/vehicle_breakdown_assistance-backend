package com.roadassist.backend.service.geocoding;

import java.math.BigDecimal;

public record GeocodedLocation(BigDecimal latitude, BigDecimal longitude) {
}

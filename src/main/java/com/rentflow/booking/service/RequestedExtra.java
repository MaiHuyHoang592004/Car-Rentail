package com.rentflow.booking.service;

import java.util.UUID;

public record RequestedExtra(UUID extraId, int quantity) {
}

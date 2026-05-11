package com.rentflow.availability.dto;

import java.time.LocalDate;
import java.util.List;

public record DateListRequest(List<LocalDate> dates) {}

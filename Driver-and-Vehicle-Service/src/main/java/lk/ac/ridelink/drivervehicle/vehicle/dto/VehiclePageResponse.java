package lk.ac.ridelink.drivervehicle.vehicle.dto;

import java.util.List;

public record VehiclePageResponse(List<VehicleResponse> items, int page, int size,
        long totalElements, int totalPages) {}

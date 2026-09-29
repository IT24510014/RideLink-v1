package lk.ac.ridelink.drivervehicle.vehicle.dto;

import java.time.Instant;
import java.time.LocalDate;
import lk.ac.ridelink.drivervehicle.vehicle.document.VehicleStatus;
import lk.ac.ridelink.drivervehicle.vehicle.document.VehicleType;

public record VehicleResponse(String id, String driverId, VehicleType vehicleType,
        String registrationNumber, String make, String model, Integer manufactureYear,
        String color, Integer seatCapacity, LocalDate insuranceExpiryDate,
        VehicleStatus status, Instant createdAt, Instant updatedAt) {}

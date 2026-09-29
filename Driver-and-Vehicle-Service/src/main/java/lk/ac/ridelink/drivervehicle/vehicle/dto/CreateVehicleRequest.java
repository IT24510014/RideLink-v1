package lk.ac.ridelink.drivervehicle.vehicle.dto;

import java.time.LocalDate;
import jakarta.validation.constraints.*;
import lk.ac.ridelink.drivervehicle.vehicle.document.VehicleType;

public record CreateVehicleRequest(
        @NotBlank @Size(max = 100) String driverId,
        @NotNull VehicleType vehicleType,
        @NotBlank @Size(max = 20)
        @Pattern(regexp = "[A-Za-z0-9 -]*[A-Za-z0-9][A-Za-z0-9 -]*",
                message = "must contain letters or digits and only letters, digits, spaces or hyphens") String registrationNumber,
        @NotBlank @Size(max = 100) String make,
        @NotBlank @Size(max = 100) String model,
        @NotNull @Min(1900) Integer manufactureYear,
        @NotBlank @Size(max = 50) String color,
        @NotNull @Min(1) @Max(50) Integer seatCapacity,
        @NotNull @Future LocalDate insuranceExpiryDate) {}

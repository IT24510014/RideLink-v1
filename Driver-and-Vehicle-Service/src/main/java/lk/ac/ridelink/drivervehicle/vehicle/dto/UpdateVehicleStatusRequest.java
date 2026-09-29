package lk.ac.ridelink.drivervehicle.vehicle.dto;

import jakarta.validation.constraints.NotNull;
import lk.ac.ridelink.drivervehicle.vehicle.document.VehicleStatus;

public record UpdateVehicleStatusRequest(@NotNull VehicleStatus status) {}

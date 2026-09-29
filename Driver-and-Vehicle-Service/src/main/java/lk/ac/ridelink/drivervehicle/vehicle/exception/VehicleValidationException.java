package lk.ac.ridelink.drivervehicle.vehicle.exception;

public class VehicleValidationException extends RuntimeException {
    private final String field;

    public VehicleValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}

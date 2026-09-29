package lk.ac.ridelink.drivervehicle.vehicle.document;

import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@Setter
@Document(collection = "vehicles")
public class Vehicle {
    @Id
    private String id;
    @Version
    private Long version;
    private String driverId;
    private VehicleType vehicleType;
    private String registrationNumber;
    private String make;
    private String model;
    private Integer manufactureYear;
    private String color;
    private Integer seatCapacity;
    private LocalDate insuranceExpiryDate;
    private VehicleStatus status;
    @CreatedDate
    private Instant createdAt;
    @LastModifiedDate
    private Instant updatedAt;
}

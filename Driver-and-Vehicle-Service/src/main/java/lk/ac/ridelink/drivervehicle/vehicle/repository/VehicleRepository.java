package lk.ac.ridelink.drivervehicle.vehicle.repository;

import lk.ac.ridelink.drivervehicle.vehicle.document.Vehicle;
import lk.ac.ridelink.drivervehicle.vehicle.document.VehicleStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface VehicleRepository extends MongoRepository<Vehicle, String> {
    boolean existsByDriverId(String driverId);
    boolean existsByRegistrationNumber(String registrationNumber);
    boolean existsByRegistrationNumberAndIdNot(String registrationNumber, String id);
    Page<Vehicle> findByDriverId(String driverId, Pageable pageable);
    Page<Vehicle> findByStatus(VehicleStatus status, Pageable pageable);
    Page<Vehicle> findByDriverIdAndStatus(String driverId, VehicleStatus status, Pageable pageable);
}

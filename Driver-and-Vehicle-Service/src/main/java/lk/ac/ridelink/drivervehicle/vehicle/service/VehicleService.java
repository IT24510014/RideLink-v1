package lk.ac.ridelink.drivervehicle.vehicle.service;

import java.time.LocalDate;
import java.util.Locale;
import lk.ac.ridelink.drivervehicle.driver.document.Driver;
import lk.ac.ridelink.drivervehicle.driver.document.DriverStatus;
import lk.ac.ridelink.drivervehicle.driver.exception.DriverNotFoundException;
import lk.ac.ridelink.drivervehicle.driver.repository.DriverRepository;
import lk.ac.ridelink.drivervehicle.vehicle.document.*;
import lk.ac.ridelink.drivervehicle.vehicle.dto.*;
import lk.ac.ridelink.drivervehicle.vehicle.exception.*;
import lk.ac.ridelink.drivervehicle.vehicle.repository.VehicleRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class VehicleService {
    private final VehicleRepository repository;
    private final DriverRepository drivers;

    public VehicleService(VehicleRepository repository, DriverRepository drivers) {
        this.repository = repository;
        this.drivers = drivers;
    }

    public VehicleResponse create(CreateVehicleRequest request) {
        String driverId = request.driverId().strip();
        findDriver(driverId);
        validateYear(request.manufactureYear());
        String registration = normalizeRegistration(request.registrationNumber());
        if (repository.existsByRegistrationNumber(registration)) {
            throw duplicateRegistration();
        }
        Vehicle vehicle = new Vehicle();
        vehicle.setDriverId(driverId);
        applyProfile(vehicle, request.vehicleType(), registration, request.make(), request.model(),
                request.manufactureYear(), request.color(), request.seatCapacity(), request.insuranceExpiryDate());
        vehicle.setStatus(VehicleStatus.PENDING);
        return response(save(vehicle));
    }

    public VehicleResponse get(String id) {
        return response(find(id));
    }

    public VehiclePageResponse list(int page, int size, VehicleStatus status, String driverId) {
        var pageable = PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id")));
        String driver = driverId == null || driverId.isBlank() ? null : driverId.strip();
        var results = driver != null
                ? (status != null ? repository.findByDriverIdAndStatus(driver, status, pageable)
                                  : repository.findByDriverId(driver, pageable))
                : (status != null ? repository.findByStatus(status, pageable)
                                  : repository.findAll(pageable));
        return new VehiclePageResponse(results.getContent().stream().map(VehicleService::response).toList(),
                results.getNumber(), results.getSize(), results.getTotalElements(), results.getTotalPages());
    }

    public VehicleResponse update(String id, UpdateVehicleRequest request) {
        Vehicle vehicle = find(id);
        Driver driver = findDriver(vehicle.getDriverId());
        validateYear(request.manufactureYear());
        String registration = normalizeRegistration(request.registrationNumber());
        if (repository.existsByRegistrationNumberAndIdNot(registration, id)) {
            throw duplicateRegistration();
        }
        if (vehicle.getStatus() == VehicleStatus.APPROVED) {
            requireApproval(driver, request.insuranceExpiryDate());
        }
        applyProfile(vehicle, request.vehicleType(), registration, request.make(), request.model(),
                request.manufactureYear(), request.color(), request.seatCapacity(), request.insuranceExpiryDate());
        return response(save(vehicle));
    }

    public VehicleResponse updateStatus(String id, UpdateVehicleStatusRequest request) {
        Vehicle vehicle = find(id);
        Driver driver = findDriver(vehicle.getDriverId());
        VehicleStatus next = request.status();
        if (vehicle.getStatus() == next) {
            return response(vehicle);
        }
        if (next == VehicleStatus.PENDING) {
            throw new VehicleConflictException("A vehicle cannot return to PENDING status.");
        }
        if (next == VehicleStatus.APPROVED) {
            requireApproval(driver, vehicle.getInsuranceExpiryDate());
        }
        vehicle.setStatus(next);
        return response(save(vehicle));
    }

    public void delete(String id) {
        Vehicle vehicle = find(id);
        try {
            repository.delete(vehicle);
        } catch (OptimisticLockingFailureException exception) {
            throw concurrentModification();
        }
    }

    private Vehicle find(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new VehicleNotFoundException("Vehicle not found."));
    }

    private Driver findDriver(String id) {
        return drivers.findById(id)
                .orElseThrow(() -> new DriverNotFoundException("Driver not found."));
    }

    private Vehicle save(Vehicle vehicle) {
        try {
            return repository.save(vehicle);
        } catch (DuplicateKeyException exception) {
            throw duplicateRegistration();
        } catch (OptimisticLockingFailureException exception) {
            throw concurrentModification();
        }
    }

    private static VehicleConflictException duplicateRegistration() {
        return new VehicleConflictException("This registration number is already registered.");
    }

    private static VehicleConflictException concurrentModification() {
        return new VehicleConflictException(
                "The vehicle was changed by another request. Reload the vehicle and try again.");
    }

    private static void validateYear(int year) {
        if (year < 1900 || year > LocalDate.now().getYear() + 1) {
            throw new VehicleValidationException("manufactureYear",
                    "must be between 1900 and the current year plus one");
        }
    }

    private static void requireApproval(Driver driver, LocalDate insuranceExpiryDate) {
        LocalDate today = LocalDate.now();
        if (driver.getStatus() != DriverStatus.APPROVED || driver.getLicenseExpiryDate() == null
                || !driver.getLicenseExpiryDate().isAfter(today)) {
            throw new VehicleConflictException("Approval requires an approved driver with an unexpired license.");
        }
        if (insuranceExpiryDate == null || !insuranceExpiryDate.isAfter(today)) {
            throw new VehicleConflictException("Approval requires unexpired vehicle insurance.");
        }
    }

    private static String normalizeRegistration(String registration) {
        return registration.strip().toUpperCase(Locale.ROOT).replace(" ", "").replace("-", "");
    }

    private static void applyProfile(Vehicle vehicle, VehicleType type, String registration,
            String make, String model, Integer year, String color, Integer seats, LocalDate insuranceExpiry) {
        vehicle.setVehicleType(type);
        vehicle.setRegistrationNumber(registration);
        vehicle.setMake(make.strip());
        vehicle.setModel(model.strip());
        vehicle.setManufactureYear(year);
        vehicle.setColor(color.strip());
        vehicle.setSeatCapacity(seats);
        vehicle.setInsuranceExpiryDate(insuranceExpiry);
    }

    private static VehicleResponse response(Vehicle vehicle) {
        return new VehicleResponse(vehicle.getId(), vehicle.getDriverId(), vehicle.getVehicleType(),
                vehicle.getRegistrationNumber(), vehicle.getMake(), vehicle.getModel(),
                vehicle.getManufactureYear(), vehicle.getColor(), vehicle.getSeatCapacity(),
                vehicle.getInsuranceExpiryDate(), vehicle.getStatus(), vehicle.getCreatedAt(), vehicle.getUpdatedAt());
    }
}

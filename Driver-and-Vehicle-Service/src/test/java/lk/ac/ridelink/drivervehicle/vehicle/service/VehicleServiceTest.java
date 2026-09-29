package lk.ac.ridelink.drivervehicle.vehicle.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lk.ac.ridelink.drivervehicle.driver.document.Driver;
import lk.ac.ridelink.drivervehicle.driver.document.DriverStatus;
import lk.ac.ridelink.drivervehicle.driver.exception.DriverNotFoundException;
import lk.ac.ridelink.drivervehicle.driver.repository.DriverRepository;
import lk.ac.ridelink.drivervehicle.vehicle.document.*;
import lk.ac.ridelink.drivervehicle.vehicle.dto.*;
import lk.ac.ridelink.drivervehicle.vehicle.exception.*;
import lk.ac.ridelink.drivervehicle.vehicle.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VehicleServiceTest {
    @Mock
    private VehicleRepository repository;
    @Mock
    private DriverRepository drivers;
    private VehicleService service;
    private Vehicle vehicle;
    private Driver driver;

    @BeforeEach
    void setUp() {
        service = new VehicleService(repository, drivers);
        driver = new Driver();
        driver.setId("driver-1");
        driver.setStatus(DriverStatus.APPROVED);
        driver.setLicenseExpiryDate(LocalDate.now().plusYears(1));
        vehicle = new Vehicle();
        vehicle.setId("vehicle-1");
        vehicle.setDriverId("driver-1");
        vehicle.setVehicleType(VehicleType.CAR);
        vehicle.setRegistrationNumber("CAB1234");
        vehicle.setStatus(VehicleStatus.PENDING);
        vehicle.setInsuranceExpiryDate(LocalDate.now().plusYears(1));
        vehicle.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        vehicle.setVersion(3L);
    }

    private CreateVehicleRequest createRequest(String registration, int year) {
        return new CreateVehicleRequest(" driver-1 ", VehicleType.CAR, registration,
                " Toyota ", " Prius ", year, " White ", 5, LocalDate.now().plusYears(1));
    }

    private UpdateVehicleRequest updateRequest(int year) {
        return new UpdateVehicleRequest(VehicleType.VAN, " cab-1234 ", " Toyota ", " Hiace ",
                year, " White ", 10, LocalDate.now().plusYears(1));
    }

    private void existing() {
        when(repository.findById("vehicle-1")).thenReturn(Optional.of(vehicle));
    }

    private void owner() {
        when(drivers.findById("driver-1")).thenReturn(Optional.of(driver));
    }

    @ParameterizedTest
    @ValueSource(strings = {"cab-1234", "CAB 1234", " CAB1234 "})
    void createsCanonicalPendingVehicle(String registration) {
        owner();
        when(repository.save(any())).thenAnswer(invocation -> {
            Vehicle saved = invocation.getArgument(0);
            saved.setId("generated");
            return saved;
        });
        var result = service.create(createRequest(registration, 2020));
        assertThat(result.id()).isEqualTo("generated");
        assertThat(result.driverId()).isEqualTo("driver-1");
        assertThat(result.registrationNumber()).isEqualTo("CAB1234");
        assertThat(result.vehicleType()).isEqualTo(VehicleType.CAR);
        assertThat(result.make()).isEqualTo("Toyota");
        assertThat(result.model()).isEqualTo("Prius");
        assertThat(result.color()).isEqualTo("White");
        assertThat(result.status()).isEqualTo(VehicleStatus.PENDING);
        verify(repository).existsByRegistrationNumber("CAB1234");
    }

    @Test
    void rejectsMissingDriverOnCreateAndUpdates() {
        assertThatThrownBy(() -> service.create(createRequest("CAB1234", 2020)))
                .isInstanceOf(DriverNotFoundException.class);
        existing();
        assertThatThrownBy(() -> service.update("vehicle-1", updateRequest(2020)))
                .isInstanceOf(DriverNotFoundException.class);
        assertThatThrownBy(() -> service.updateStatus("vehicle-1",
                new UpdateVehicleStatusRequest(VehicleStatus.APPROVED)))
                .isInstanceOf(DriverNotFoundException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void rejectsDuplicatesOnCreateAndUpdate() {
        owner();
        when(repository.existsByRegistrationNumber("CAB1234")).thenReturn(true);
        assertThatThrownBy(() -> service.create(createRequest("cab-1234", 2020)))
                .isInstanceOf(VehicleConflictException.class);
        existing();
        when(repository.existsByRegistrationNumberAndIdNot("CAB1234", "vehicle-1")).thenReturn(true);
        assertThatThrownBy(() -> service.update("vehicle-1", updateRequest(2020)))
                .isInstanceOf(VehicleConflictException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void validatesDynamicYearOnCreateAndUpdate() {
        owner();
        for (int year : new int[] {1899, LocalDate.now().getYear() + 2}) {
            assertThatThrownBy(() -> service.create(createRequest("CAB1234", year)))
                    .isInstanceOf(VehicleValidationException.class);
        }
        existing();
        assertThatThrownBy(() -> service.update("vehicle-1", updateRequest(LocalDate.now().getYear() + 2)))
                .isInstanceOf(VehicleValidationException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void acceptsYearBoundaries() {
        owner();
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        assertThat(service.create(createRequest("CAB1234", 1900)).manufactureYear()).isEqualTo(1900);
        int nextYear = LocalDate.now().getYear() + 1;
        assertThat(service.create(createRequest("CAB1234", nextYear)).manufactureYear()).isEqualTo(nextYear);
    }

    @Test
    void readsAndUpdatesPreservingIdentityStatusAndVersion() {
        existing();
        owner();
        vehicle.setStatus(VehicleStatus.APPROVED);
        when(repository.save(vehicle)).thenReturn(vehicle);
        assertThat(service.get("vehicle-1").driverId()).isEqualTo("driver-1");
        var result = service.update("vehicle-1", updateRequest(2020));
        assertThat(result.id()).isEqualTo("vehicle-1");
        assertThat(result.driverId()).isEqualTo("driver-1");
        assertThat(result.vehicleType()).isEqualTo(VehicleType.VAN);
        assertThat(result.status()).isEqualTo(VehicleStatus.APPROVED);
        assertThat(result.createdAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
        assertThat(vehicle.getVersion()).isEqualTo(3L);
        verify(repository).existsByRegistrationNumberAndIdNot("CAB1234", "vehicle-1");
    }

    @Test
    void approvedProfileUpdateRequiresEligibleDriver() {
        existing();
        owner();
        vehicle.setStatus(VehicleStatus.APPROVED);
        driver.setStatus(DriverStatus.SUSPENDED);
        assertThatThrownBy(() -> service.update("vehicle-1", updateRequest(2020)))
                .isInstanceOf(VehicleConflictException.class);
        assertThat(vehicle.getVehicleType()).isEqualTo(VehicleType.CAR);
        verify(repository, never()).save(any());
    }

    @ParameterizedTest
    @CsvSource({"PENDING,APPROVED", "PENDING,SUSPENDED", "APPROVED,SUSPENDED", "SUSPENDED,APPROVED"})
    void permitsTransitions(VehicleStatus from, VehicleStatus to) {
        existing();
        owner();
        vehicle.setStatus(from);
        when(repository.save(vehicle)).thenReturn(vehicle);
        assertThat(service.updateStatus("vehicle-1", new UpdateVehicleStatusRequest(to)).status()).isEqualTo(to);
    }

    @ParameterizedTest
    @EnumSource(VehicleStatus.class)
    void repeatedStatusDoesNotWrite(VehicleStatus status) {
        existing();
        owner();
        vehicle.setStatus(status);
        assertThat(service.updateStatus("vehicle-1", new UpdateVehicleStatusRequest(status)).status()).isEqualTo(status);
        verify(repository, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = VehicleStatus.class, names = {"APPROVED", "SUSPENDED"})
    void rejectsReturnToPending(VehicleStatus from) {
        existing();
        owner();
        vehicle.setStatus(from);
        assertThatThrownBy(() -> service.updateStatus("vehicle-1",
                new UpdateVehicleStatusRequest(VehicleStatus.PENDING))).isInstanceOf(VehicleConflictException.class);
        verify(repository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"pending", "suspended", "licenseToday", "licensePast", "insuranceToday", "insurancePast"})
    void rejectsIneligibleApproval(String condition) {
        existing();
        owner();
        switch (condition) {
            case "pending" -> driver.setStatus(DriverStatus.PENDING);
            case "suspended" -> driver.setStatus(DriverStatus.SUSPENDED);
            case "licenseToday" -> driver.setLicenseExpiryDate(LocalDate.now());
            case "licensePast" -> driver.setLicenseExpiryDate(LocalDate.now().minusDays(1));
            case "insuranceToday" -> vehicle.setInsuranceExpiryDate(LocalDate.now());
            case "insurancePast" -> vehicle.setInsuranceExpiryDate(LocalDate.now().minusDays(1));
            default -> throw new AssertionError(condition);
        }
        assertThatThrownBy(() -> service.updateStatus("vehicle-1",
                new UpdateVehicleStatusRequest(VehicleStatus.APPROVED))).isInstanceOf(VehicleConflictException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void missingVehicleFailsEveryIdOperation() {
        assertThatThrownBy(() -> service.get("missing")).isInstanceOf(VehicleNotFoundException.class);
        assertThatThrownBy(() -> service.update("missing", updateRequest(2020))).isInstanceOf(VehicleNotFoundException.class);
        assertThatThrownBy(() -> service.updateStatus("missing",
                new UpdateVehicleStatusRequest(VehicleStatus.APPROVED))).isInstanceOf(VehicleNotFoundException.class);
        assertThatThrownBy(() -> service.delete("missing")).isInstanceOf(VehicleNotFoundException.class);
        verify(repository, never()).delete(any(Vehicle.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"create", "update"})
    void translatesDatabaseDuplicateRaces(String operation) {
        owner();
        when(repository.save(any())).thenThrow(new DuplicateKeyException("private database detail"));
        if (operation.equals("create")) {
            assertThatThrownBy(() -> service.create(createRequest("CAB1234", 2020)))
                    .isInstanceOf(VehicleConflictException.class)
                    .hasMessage("This registration number is already registered.");
        } else {
            existing();
            assertThatThrownBy(() -> service.update("vehicle-1", updateRequest(2020)))
                    .isInstanceOf(VehicleConflictException.class)
                    .hasMessage("This registration number is already registered.");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"update", "status", "delete"})
    void translatesOptimisticLockFailures(String operation) {
        existing();
        var failure = new OptimisticLockingFailureException("private version detail");
        Runnable action;
        if (operation.equals("delete")) {
            doThrow(failure).when(repository).delete(vehicle);
            action = () -> service.delete("vehicle-1");
        } else {
            owner();
            when(repository.save(vehicle)).thenThrow(failure);
            action = operation.equals("update") ? () -> service.update("vehicle-1", updateRequest(2020))
                    : () -> service.updateStatus("vehicle-1", new UpdateVehicleStatusRequest(VehicleStatus.SUSPENDED));
        }
        assertThatThrownBy(action::run).isInstanceOf(VehicleConflictException.class)
                .hasMessage("The vehicle was changed by another request. Reload the vehicle and try again.");
    }

    @Test
    void deletesLoadedVersionedEntityWithoutRequiringDriver() {
        existing();
        service.delete("vehicle-1");
        verify(repository).delete(vehicle);
        verifyNoInteractions(drivers);
    }

    @ParameterizedTest
    @ValueSource(strings = {"none", "blank", "driver", "status", "both"})
    void listsAllFilterCombinationsWithStablePagination(String filter) {
        String driverId = filter.equals("driver") || filter.equals("both") ? " driver-1 "
                : filter.equals("blank") ? "  " : null;
        VehicleStatus status = filter.equals("status") || filter.equals("both") ? VehicleStatus.PENDING : null;
        org.mockito.stubbing.Answer<org.springframework.data.domain.Page<Vehicle>> answer = invocation -> {
            Pageable pageable = invocation.getArgument(invocation.getArguments().length - 1);
            assertThat(pageable.getPageNumber()).isEqualTo(1);
            assertThat(pageable.getPageSize()).isEqualTo(5);
            assertThat(pageable.getSort()).isEqualTo(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id")));
            return new PageImpl<>(List.of(vehicle), pageable, 6);
        };
        switch (filter) {
            case "driver" -> when(repository.findByDriverId(eq("driver-1"), any())).thenAnswer(answer);
            case "status" -> when(repository.findByStatus(eq(status), any())).thenAnswer(answer);
            case "both" -> when(repository.findByDriverIdAndStatus(eq("driver-1"), eq(status), any())).thenAnswer(answer);
            default -> when(repository.findAll(any(Pageable.class))).thenAnswer(answer);
        }
        var result = service.list(1, 5, status, driverId);
        assertThat(result.items()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(6);
        assertThat(result.totalPages()).isEqualTo(2);
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(5);
    }
}

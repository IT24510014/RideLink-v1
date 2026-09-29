package lk.ac.ridelink.drivervehicle.vehicle.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lk.ac.ridelink.drivervehicle.vehicle.document.VehicleStatus;
import lk.ac.ridelink.drivervehicle.vehicle.dto.*;
import lk.ac.ridelink.drivervehicle.vehicle.service.VehicleService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/vehicles")
public class VehicleController {
    private final VehicleService service;

    public VehicleController(VehicleService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<VehicleResponse> create(@Valid @RequestBody CreateVehicleRequest request) {
        VehicleResponse vehicle = service.create(request);
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(vehicle.id()).toUri();
        return ResponseEntity.created(location).body(vehicle);
    }

    @GetMapping("/{id}")
    public VehicleResponse get(@PathVariable String id) {
        return service.get(id);
    }

    @GetMapping
    public VehiclePageResponse list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) VehicleStatus status,
            @RequestParam(required = false) String driverId) {
        return service.list(page, size, status, driverId);
    }

    @PutMapping("/{id}")
    public VehicleResponse update(@PathVariable String id,
            @Valid @RequestBody UpdateVehicleRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public VehicleResponse updateStatus(@PathVariable String id,
            @Valid @RequestBody UpdateVehicleStatusRequest request) {
        return service.updateStatus(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}

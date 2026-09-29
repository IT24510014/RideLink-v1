package lk.ac.ridelink.drivervehicle.vehicle.controller;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lk.ac.ridelink.drivervehicle.config.DevSecurityConfig;
import lk.ac.ridelink.drivervehicle.driver.exception.DriverNotFoundException;
import lk.ac.ridelink.drivervehicle.vehicle.document.*;
import lk.ac.ridelink.drivervehicle.vehicle.dto.*;
import lk.ac.ridelink.drivervehicle.vehicle.exception.*;
import lk.ac.ridelink.drivervehicle.vehicle.service.VehicleService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = VehicleController.class,
        properties = "spring.config.location=classpath:/driver-test.properties")
@WithMockUser
@ActiveProfiles("dev")
@Import(DevSecurityConfig.class)
class VehicleControllerTest {
    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private VehicleService service;

    private VehicleResponse response(VehicleType type) {
        return new VehicleResponse("vehicle-1", "driver-1", type, "CAB1234", "Toyota", "Prius",
                2020, "White", 5, LocalDate.now().plusYears(1), VehicleStatus.PENDING,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"));
    }

    private String profile() {
        return """
                {"vehicleType":"CAR","registrationNumber":"CAB-1234","make":"Toyota","model":"Prius",
                 "manufactureYear":2020,"color":"White","seatCapacity":5,"insuranceExpiryDate":"%s"}
                """.formatted(LocalDate.now().plusYears(1));
    }

    private String registration() {
        return profile().replace("{", "{\"driverId\":\"driver-1\",");
    }

    @ParameterizedTest
    @EnumSource(VehicleType.class)
    void createsAllVehicleTypesWithLocation(VehicleType type) throws Exception {
        when(service.create(any())).thenReturn(response(type));
        mvc.perform(post("/api/v1/vehicles").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(registration().replace("CAR", type.name())))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/v1/vehicles/vehicle-1"))
                .andExpect(jsonPath("$.vehicleType").value(type.name()))
                .andExpect(jsonPath("$.status").value("PENDING"));
        verify(service).create(argThat(request -> request.vehicleType() == type));
    }

    @Test
    void getsVehicleWithoutInternalVersion() throws Exception {
        when(service.get("vehicle-1")).thenReturn(response(VehicleType.CAR));
        mvc.perform(get("/api/v1/vehicles/vehicle-1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.driverId").value("driver-1"))
                .andExpect(jsonPath("$.vehicleType").value("CAR"))
                .andExpect(jsonPath("$.version").doesNotExist());
    }

    @Test
    void listsDefaultsAndFilters() throws Exception {
        when(service.list(0, 20, null, null))
                .thenReturn(new VehiclePageResponse(List.of(response(VehicleType.CAR)), 0, 20, 1, 1));
        mvc.perform(get("/api/v1/vehicles")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value("vehicle-1"))
                .andExpect(jsonPath("$.totalElements").value(1));
        when(service.list(1, 5, VehicleStatus.PENDING, "driver-1"))
                .thenReturn(new VehiclePageResponse(List.of(), 1, 5, 0, 0));
        mvc.perform(get("/api/v1/vehicles").param("page", "1").param("size", "5")
                        .param("status", "PENDING").param("driverId", "driver-1"))
                .andExpect(status().isOk());
        verify(service).list(1, 5, VehicleStatus.PENDING, "driver-1");
    }

    @ParameterizedTest
    @EnumSource(VehicleType.class)
    void updatesAllVehicleTypes(VehicleType type) throws Exception {
        when(service.update(eq("vehicle-1"), any())).thenReturn(response(type));
        mvc.perform(put("/api/v1/vehicles/vehicle-1").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(profile().replace("CAR", type.name())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.vehicleType").value(type.name()));
        verify(service).update(eq("vehicle-1"), argThat(request -> request.vehicleType() == type));
    }

    @Test
    void updatesStatusAndDeletes() throws Exception {
        when(service.updateStatus(eq("vehicle-1"), any())).thenReturn(response(VehicleType.CAR));
        mvc.perform(patch("/api/v1/vehicles/vehicle-1/status").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"SUSPENDED\"}"))
                .andExpect(status().isOk());
        verify(service).updateStatus("vehicle-1", new UpdateVehicleStatusRequest(VehicleStatus.SUSPENDED));
        mvc.perform(delete("/api/v1/vehicles/vehicle-1").with(csrf()))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        verify(service).delete("vehicle-1");
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "null", "unknown"})
    void requiresValidTypeOnCreateAndUpdate(String invalid) throws Exception {
        String replacement = switch (invalid) {
            case "missing" -> "";
            case "null" -> "\"vehicleType\":null,";
            default -> "\"vehicleType\":\"BUS\",";
        };
        mvc.perform(post("/api/v1/vehicles").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(registration().replace("\"vehicleType\":\"CAR\",", replacement)))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/vehicles/vehicle-1").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(profile().replace("\"vehicleType\":\"CAR\",", replacement)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void missingFieldsReturnFieldErrors() throws Exception {
        mvc.perform(post("/api/v1/vehicles").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.driverId").exists())
                .andExpect(jsonPath("$.errors.vehicleType").exists())
                .andExpect(jsonPath("$.errors.registrationNumber").exists())
                .andExpect(jsonPath("$.errors.insuranceExpiryDate").exists());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"registration", "seatsLow", "seatsHigh", "year", "insurance", "blankMake", "longModel"})
    void rejectsInvalidProfileOnCreateAndUpdate(String field) throws Exception {
        String invalid = switch (field) {
            case "registration" -> profile().replace("CAB-1234", "---");
            case "seatsLow" -> profile().replace("\"seatCapacity\":5", "\"seatCapacity\":0");
            case "seatsHigh" -> profile().replace("\"seatCapacity\":5", "\"seatCapacity\":51");
            case "year" -> profile().replace("2020", "1899");
            case "insurance" -> profile().replace(LocalDate.now().plusYears(1).toString(), LocalDate.now().toString());
            case "blankMake" -> profile().replace("Toyota", " ");
            default -> profile().replace("Prius", "x".repeat(101));
        };
        mvc.perform(put("/api/v1/vehicles/vehicle-1").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/vehicles").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(invalid.replace("{", "{\"driverId\":\"driver-1\",")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"status\":\"UNKNOWN\"}", "not-json"})
    void rejectsInvalidStatus(String body) throws Exception {
        mvc.perform(patch("/api/v1/vehicles/vehicle-1/status").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=-1", "size=0", "size=101", "page=abc", "status=UNKNOWN"})
    void rejectsInvalidPagination(String query) throws Exception {
        mvc.perform(get("/api/v1/vehicles?" + query)).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void returnsNotFoundForVehicleAndDriver() throws Exception {
        when(service.get("missing")).thenThrow(new VehicleNotFoundException("Vehicle not found."));
        mvc.perform(get("/api/v1/vehicles/missing")).andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        when(service.create(any())).thenThrow(new DriverNotFoundException("Driver not found."));
        mvc.perform(post("/api/v1/vehicles").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(registration())).andExpect(status().isNotFound());
        doThrow(new VehicleNotFoundException("Vehicle not found.")).when(service).delete("missing");
        mvc.perform(delete("/api/v1/vehicles/missing").with(csrf())).andExpect(status().isNotFound());
    }

    @Test
    void returnsConflictAndDynamicValidationErrors() throws Exception {
        when(service.create(any())).thenThrow(new VehicleConflictException("This registration number is already registered."));
        mvc.perform(post("/api/v1/vehicles").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(registration())).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("This registration number is already registered."));
        when(service.update(eq("vehicle-1"), any()))
                .thenThrow(new VehicleValidationException("manufactureYear", "year is too far in the future"));
        mvc.perform(put("/api/v1/vehicles/vehicle-1").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(profile())).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.manufactureYear").value("year is too far in the future"));
    }

    @Test
    void unexpectedFailureDoesNotExposeDetails() throws Exception {
        when(service.get("vehicle-1")).thenThrow(new IllegalStateException("private detail"));
        mvc.perform(get("/api/v1/vehicles/vehicle-1")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred."));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/vehicles", "/api/v1/vehicles/vehicle-1"})
    void requiresAuthentication(String path) throws Exception {
        mvc.perform(get(path).with(anonymous()).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void preservesDevAuthenticatedWritesWithoutCsrf() throws Exception {
        mvc.perform(delete("/api/v1/vehicles/vehicle-1")).andExpect(status().isNoContent());
        verify(service).delete("vehicle-1");
    }

    @Test
    void anonymousCannotWriteEvenWithCsrf() throws Exception {
        mvc.perform(delete("/api/v1/vehicles/vehicle-1").with(anonymous()).with(csrf()))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
}

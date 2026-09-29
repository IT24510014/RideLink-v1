package lk.ac.ridelink.drivervehicle.config;

import lk.ac.ridelink.drivervehicle.vehicle.document.Vehicle;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

@Configuration
public class VehicleIndexConfig {
    @Bean
    SmartInitializingSingleton vehicleIndexes(MongoTemplate template) {
        // Complete index creation before lifecycle components, including the web server, start.
        return () -> {
            var indexes = template.indexOps(Vehicle.class);
            indexes.createIndex(new Index().on("registrationNumber", Sort.Direction.ASC)
                    .unique().named("vehicle_registration_unique"));
            indexes.createIndex(ordered(new Index()).named("vehicle_created"));
            indexes.createIndex(ordered(new Index().on("driverId", Sort.Direction.ASC)).named("vehicle_driver_created"));
            indexes.createIndex(ordered(new Index().on("status", Sort.Direction.ASC)).named("vehicle_status_created"));
            indexes.createIndex(ordered(new Index().on("driverId", Sort.Direction.ASC)
                    .on("status", Sort.Direction.ASC)).named("vehicle_driver_status_created"));
        };
    }

    private static Index ordered(Index index) {
        return index.on("createdAt", Sort.Direction.DESC).on("_id", Sort.Direction.ASC);
    }
}

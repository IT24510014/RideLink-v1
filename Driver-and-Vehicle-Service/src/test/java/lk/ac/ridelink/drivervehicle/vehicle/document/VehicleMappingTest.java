package lk.ac.ridelink.drivervehicle.vehicle.document;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.convert.NoOpDbRefResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

import static org.assertj.core.api.Assertions.*;

class VehicleMappingTest {
    @Test
    void mapsCollectionVersionAuditingAndEnums() {
        var context = new MongoMappingContext();
        var conversions = MongoCustomConversions.create(adapter -> {});
        context.setSimpleTypeHolder(conversions.getSimpleTypeHolder());
        var entity = context.getRequiredPersistentEntity(Vehicle.class);
        assertThat(entity.getCollection()).isEqualTo("vehicles");
        assertThat(entity.getRequiredVersionProperty().getName()).isEqualTo("version");
        assertThat(entity.getRequiredPersistentProperty("createdAt").isAnnotationPresent(CreatedDate.class)).isTrue();
        assertThat(entity.getRequiredPersistentProperty("updatedAt").isAnnotationPresent(LastModifiedDate.class)).isTrue();
        Vehicle vehicle = new Vehicle();
        assertThat(entity.isNew(vehicle)).isTrue();
        vehicle.setId("vehicle-1");
        vehicle.setVersion(0L);
        vehicle.setVehicleType(VehicleType.THREE_WHEELER);
        vehicle.setStatus(VehicleStatus.PENDING);
        assertThat(entity.isNew(vehicle)).isFalse();
        var converter = new MappingMongoConverter(NoOpDbRefResolver.INSTANCE, context);
        converter.setCustomConversions(conversions);
        converter.afterPropertiesSet();
        Document document = new Document();
        converter.write(vehicle, document);
        assertThat(document).containsEntry("vehicleType", "THREE_WHEELER").containsEntry("status", "PENDING");
        assertThat(converter.read(Vehicle.class, document).getVehicleType()).isEqualTo(VehicleType.THREE_WHEELER);
    }
}

package org.example.location.repository;

import org.example.location.entity.AmbulanceLocation;
import org.springframework.data.repository.CrudRepository;
import java.util.UUID;

public interface LocationRepository extends CrudRepository<AmbulanceLocation, UUID> {
}
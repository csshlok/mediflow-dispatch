package org.example.hospital.service;

import org.example.hospital.entity.BedReservation;
import org.example.hospital.entity.Hospital;
import org.example.hospital.repository.BedReservationRepository;
import org.example.hospital.repository.HospitalRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class HospitalService {

    private final HospitalRepository repository;
    private final BedReservationRepository reservationRepository;
    private final boolean forceReservationFailure;

    public HospitalService(HospitalRepository repository,
                           BedReservationRepository reservationRepository,
                           @Value("${FORCE_RESERVATION_FAILURE:false}") boolean forceReservationFailure) {
        this.repository = repository;
        this.reservationRepository = reservationRepository;
        this.forceReservationFailure = forceReservationFailure;
    }

    public List<Hospital> getAvailableHospitals(int minBeds) {
        return repository.findByAvailableBedsGreaterThan(minBeds - 1); // e.g., > 0
    }

    // Takes one bed under the given key. Repeating the call with the same key does not take another bed.
    @Transactional
    public void reserveBed(UUID hospitalId, String reservationKey) {
        Optional<BedReservation> existing = reservationRepository.findByKeyForUpdate(reservationKey);
        if (existing.isPresent()) {
            BedReservation reservation = existing.get();
            if (!reservation.getHospitalId().equals(hospitalId)) {
                throw new IllegalStateException("Reservation key already used for another hospital");
            }
            if (reservation.getStatus() == BedReservation.Status.RELEASED) {
                throw new IllegalStateException("Reservation " + reservationKey + " was already released");
            }
            return;
        }

        if (forceReservationFailure) {
            throw new IllegalStateException("Forced reservation failure for saga recovery test");
        }

        if (!repository.existsById(hospitalId)) {
            throw new HospitalNotFoundException(hospitalId);
        }

        if (repository.takeBed(hospitalId) == 0) {
            throw new IllegalStateException("No available beds at hospital: " + hospitalId);
        }

        reservationRepository.save(new BedReservation(reservationKey, hospitalId, BedReservation.Status.RESERVED));
    }

    // Saga compensation: returns the bed taken under this key, at most once.
    @Transactional
    public void releaseBed(UUID hospitalId, String reservationKey) {
        Optional<BedReservation> existing = reservationRepository.findByKeyForUpdate(reservationKey);
        if (existing.isEmpty()) {
            // Nothing was reserved; record the release so a late reserve with this key cannot take a bed
            reservationRepository.save(new BedReservation(reservationKey, hospitalId, BedReservation.Status.RELEASED));
            return;
        }

        BedReservation reservation = existing.get();
        if (reservation.getStatus() == BedReservation.Status.RELEASED) {
            return;
        }

        repository.returnBed(reservation.getHospitalId());
        reservation.markReleased();
        reservationRepository.save(reservation);
    }

    public Hospital registerHospital(Hospital request) {
        if (request.getName() == null || request.getName().isBlank()) {
            throw new IllegalArgumentException("hospitalName is required");
        }
        if (request.getAvailableBeds() == null || request.getAvailableBeds() < 0) {
            throw new IllegalArgumentException("availableBeds must be zero or more");
        }

        // Build a fresh entity so client-supplied ids or versions are ignored
        return repository.save(new Hospital(request.getName(), request.getAvailableBeds(),
                request.getLatitude(), request.getLongitude()));
    }
}

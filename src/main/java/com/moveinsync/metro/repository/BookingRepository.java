package com.moveinsync.metro.repository;

import com.moveinsync.metro.model.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, String> {

    Optional<Booking> findByQrString(String qrString);

    Optional<Booking> findByUserIdAndSourceStopIdAndDestinationStopIdAndStatus(
            String userId, String sourceStopId, String destinationStopId, String status);
}
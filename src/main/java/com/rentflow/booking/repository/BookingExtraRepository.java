package com.rentflow.booking.repository;

import com.rentflow.booking.entity.BookingExtra;
import com.rentflow.booking.entity.BookingExtraId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BookingExtraRepository extends JpaRepository<BookingExtra, BookingExtraId> {
}

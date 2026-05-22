package com.rentflow.listing.repository;

import com.rentflow.listing.dto.ListingSearchCriteria;
import com.rentflow.listing.entity.Listing;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Repository
@RequiredArgsConstructor
public class ListingSearchRepositoryCustomImpl implements ListingSearchRepositoryCustom {

    @PersistenceContext
    private EntityManager em;

    @Override
    @Transactional(readOnly = true)
    public Page<Listing> search(ListingSearchCriteria criteria, Pageable pageable) {
        if (criteria.pickupDate() != null && criteria.returnDate() != null) {
            return searchWithDates(criteria, pageable);
        }
        return searchWithoutDates(criteria, pageable);
    }

    private Page<Listing> searchWithoutDates(ListingSearchCriteria criteria, Pageable pageable) {
        String baseSelect = """
            SELECT DISTINCT l.* FROM listings l
            JOIN vehicles v ON l.vehicle_id = v.id
            WHERE l.status = 'ACTIVE'
            """;

        StringBuilder dynamic = buildDynamicFilters(criteria);
        String orderBy = " ORDER BY l.created_at DESC";
        String dataSql = baseSelect + dynamic + orderBy;
        String countSql = "SELECT COUNT(*) FROM (" + baseSelect + dynamic + ") AS t";

        Query dataQuery = em.createNativeQuery(dataSql, Listing.class);
        dataQuery.setFirstResult((int) pageable.getOffset());
        dataQuery.setMaxResults(pageable.getPageSize());
        bindCommonParams(dataQuery, criteria, false);

        Query countQuery = em.createNativeQuery(countSql);
        bindCommonParams(countQuery, criteria, false);

        List<?> data = dataQuery.getResultList();
        Number total = (Number) countQuery.getSingleResult();
        @SuppressWarnings("unchecked")
        List<Listing> results = (List<Listing>) data;
        return new PageImpl<>(results, pageable, total.longValue());
    }

    private Page<Listing> searchWithDates(ListingSearchCriteria criteria, Pageable pageable) {
        String baseSelect = """
            SELECT DISTINCT l.* FROM listings l
            JOIN vehicles v ON l.vehicle_id = v.id
            WHERE l.status = 'ACTIVE'
            """;

        String availabilityFilter = """
            AND NOT EXISTS (
                SELECT 1 FROM generate_series(
                    CAST(:pickupDate AS date),
                    (CAST(:returnDate AS date) - INTERVAL '1 day'),
                    '1 day'::interval
                ) AS ds(d)
                WHERE NOT EXISTS (
                    SELECT 1 FROM availability_calendar ac
                    WHERE ac.listing_id = l.id AND ac.available_date = ds.d::date
                )
            )
            AND NOT EXISTS (
                SELECT 1 FROM availability_calendar ac
                WHERE ac.listing_id = l.id
                  AND ac.available_date >= :pickupDate
                  AND ac.available_date < :returnDate
                  AND ac.status IN ('HOLD', 'BOOKED', 'BLOCKED')
            )
            """;

        StringBuilder dynamic = buildDynamicFilters(criteria);
        String orderBy = " ORDER BY l.created_at DESC";
        String dataSql = baseSelect + availabilityFilter + dynamic + orderBy;
        String countSql = "SELECT COUNT(*) FROM (" + baseSelect + availabilityFilter + dynamic + ") AS t";

        Query dataQuery = em.createNativeQuery(dataSql, Listing.class);
        dataQuery.setFirstResult((int) pageable.getOffset());
        dataQuery.setMaxResults(pageable.getPageSize());
        bindCommonParams(dataQuery, criteria, true);

        Query countQuery = em.createNativeQuery(countSql);
        bindCommonParams(countQuery, criteria, true);

        List<?> data = dataQuery.getResultList();
        Number total = (Number) countQuery.getSingleResult();
        @SuppressWarnings("unchecked")
        List<Listing> results = (List<Listing>) data;
        return new PageImpl<>(results, pageable, total.longValue());
    }

    private StringBuilder buildDynamicFilters(ListingSearchCriteria criteria) {
        StringBuilder dynamic = new StringBuilder();
        if (criteria.city() != null) {
            dynamic.append(" AND l.city ILIKE :city || '%'");
        }
        if (criteria.categories() != null && !criteria.categories().isEmpty()) {
            String inClause = IntStream.range(0, criteria.categories().size())
                .mapToObj(i -> ":cat" + i)
                .collect(Collectors.joining(", ", " AND v.category IN (", ")"));
            dynamic.append(inClause);
        }
        if (criteria.minPrice() != null) {
            dynamic.append(" AND l.base_price_per_day >= :minPrice");
        }
        if (criteria.maxPrice() != null) {
            dynamic.append(" AND l.base_price_per_day <= :maxPrice");
        }
        if (criteria.seats() != null) {
            dynamic.append(" AND v.seats >= :seats");
        }
        if (criteria.transmission() != null) {
            dynamic.append(" AND v.transmission = :transmission");
        }
        if (criteria.fuelType() != null) {
            dynamic.append(" AND v.fuel_type = :fuelType");
        }
        return dynamic;
    }

    private void bindCommonParams(Query query, ListingSearchCriteria criteria, boolean includeDates) {
        if (includeDates) {
            query.setParameter("pickupDate", criteria.pickupDate());
            query.setParameter("returnDate", criteria.returnDate());
        }
        if (criteria.city() != null) {
            query.setParameter("city", criteria.city().trim());
        }
        if (criteria.categories() != null && !criteria.categories().isEmpty()) {
            for (int i = 0; i < criteria.categories().size(); i++) {
                query.setParameter("cat" + i, criteria.categories().get(i).name());
            }
        }
        if (criteria.minPrice() != null) {
            query.setParameter("minPrice", criteria.minPrice());
        }
        if (criteria.maxPrice() != null) {
            query.setParameter("maxPrice", criteria.maxPrice());
        }
        if (criteria.seats() != null) {
            query.setParameter("seats", criteria.seats());
        }
        if (criteria.transmission() != null) {
            query.setParameter("transmission", criteria.transmission().name());
        }
        if (criteria.fuelType() != null) {
            query.setParameter("fuelType", criteria.fuelType().name());
        }
    }
}

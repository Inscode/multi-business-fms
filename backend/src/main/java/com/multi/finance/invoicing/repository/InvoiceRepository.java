package com.multi.finance.invoicing.repository;

import com.multi.finance.invoicing.entity.Invoice;
import com.multi.finance.invoicing.enums.InvoiceMethod;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    Optional<Invoice> findByInvoiceNo(String invoiceNo);

    @Query("SELECT i FROM Invoice i JOIN FETCH i.customer WHERE i.id = :id")
    Optional<Invoice> findByIdWithCustomer(@Param("id") Long id);

    @Query("SELECT i FROM Invoice i JOIN FETCH i.customer JOIN FETCH i.lines l JOIN FETCH l.item JOIN FETCH l.brand WHERE i.id = :id")
    Optional<Invoice> findByIdWithDetails(@Param("id") Long id);

    @Query(value = "SELECT i FROM Invoice i JOIN FETCH i.customer c WHERE " +
           "(:method IS NULL OR i.method = :method) AND " +
           "(:from IS NULL OR i.invoiceDate >= :from) AND " +
           "(:to IS NULL OR i.invoiceDate <= :to) AND " +
           "(:search IS NULL OR LOWER(i.invoiceNo) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) OR LOWER(c.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))",
           countQuery = "SELECT count(i) FROM Invoice i JOIN i.customer c WHERE " +
           "(:method IS NULL OR i.method = :method) AND " +
           "(:from IS NULL OR i.invoiceDate >= :from) AND " +
           "(:to IS NULL OR i.invoiceDate <= :to) AND " +
           "(:search IS NULL OR LOWER(i.invoiceNo) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) OR LOWER(c.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))")
    Page<Invoice> search(@Param("method") InvoiceMethod method,
                         @Param("from") LocalDate from,
                         @Param("to") LocalDate to,
                         @Param("search") String search,
                         Pageable pageable);

    // Next sequence value is managed via Postgres sequences — this just checks the latest for display
    @Query(value = "SELECT NEXTVAL(:seqName)", nativeQuery = true)
    Long nextSequenceValue(@Param("seqName") String seqName);
}

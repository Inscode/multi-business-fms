package com.multi.finance.invoicing.repository;

import com.multi.finance.invoicing.entity.Invoice;
import com.multi.finance.invoicing.enums.InvoiceMethod;
import com.multi.finance.invoicing.enums.InvoiceSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    Optional<Invoice> findByInvoiceNo(String invoiceNo);

    @Query("SELECT i FROM Invoice i JOIN FETCH i.customer WHERE i.id = :id")
    Optional<Invoice> findByIdWithCustomer(@Param("id") Long id);

    @Query("SELECT i FROM Invoice i JOIN FETCH i.customer JOIN FETCH i.lines l JOIN FETCH l.item JOIN FETCH l.brand WHERE i.id = :id")
    Optional<Invoice> findByIdWithDetails(@Param("id") Long id);

    /**
     * The invoice standing behind a bill, with its lines. Returns pick from these lines
     * so a credit reverses exactly what was charged.
     */
    @Query("SELECT DISTINCT i FROM Invoice i LEFT JOIN FETCH i.lines l LEFT JOIN FETCH l.item "
         + "WHERE i.billId = :billId")
    Optional<Invoice> findByBillIdWithLines(@Param("billId") Long billId);

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

    long countByReviewedFalse();

    @Query("SELECT DISTINCT i FROM Invoice i JOIN FETCH i.customer JOIN FETCH i.lines l " +
           "JOIN FETCH l.item JOIN FETCH l.brand WHERE i.importBatchId = :batchId " +
           "ORDER BY i.id")
    List<Invoice> findByImportBatchIdWithDetails(@Param("batchId") Long batchId);

    /** Guards re-imports: the same agent invoice number can only land once per method. */
    boolean existsByExternalRefAndMethod(String externalRef, InvoiceMethod method);

    /**
     * The admin review queue. Ordered newest first — the point of the screen is
     * "what has come in", so the most recent entry belongs at the top.
     */
    @Query(value = "SELECT i FROM Invoice i JOIN FETCH i.customer c WHERE " +
           "(:reviewed IS NULL OR i.reviewed = :reviewed) AND " +
           "(:source IS NULL OR i.source = :source) AND " +
           "(:changedOnly = false OR i.customerChanged = true) AND " +
           "(:from IS NULL OR i.invoiceDate >= :from) AND " +
           "(:to IS NULL OR i.invoiceDate <= :to) AND " +
           "(:search IS NULL OR LOWER(i.invoiceNo) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           " OR LOWER(c.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           " OR LOWER(COALESCE(i.billedName, '')) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           " OR LOWER(COALESCE(i.externalRef, '')) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))) " +
           "ORDER BY i.createdAt DESC",
           countQuery = "SELECT count(i) FROM Invoice i JOIN i.customer c WHERE " +
           "(:reviewed IS NULL OR i.reviewed = :reviewed) AND " +
           "(:source IS NULL OR i.source = :source) AND " +
           "(:changedOnly = false OR i.customerChanged = true) AND " +
           "(:from IS NULL OR i.invoiceDate >= :from) AND " +
           "(:to IS NULL OR i.invoiceDate <= :to) AND " +
           "(:search IS NULL OR LOWER(i.invoiceNo) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           " OR LOWER(c.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           " OR LOWER(COALESCE(i.billedName, '')) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           " OR LOWER(COALESCE(i.externalRef, '')) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))")
    Page<Invoice> reviewSearch(@Param("reviewed") Boolean reviewed,
                               @Param("source") InvoiceSource source,
                               @Param("changedOnly") boolean changedOnly,
                               @Param("from") LocalDate from,
                               @Param("to") LocalDate to,
                               @Param("search") String search,
                               Pageable pageable);

    // Next sequence value is managed via Postgres sequences — this just checks the latest for display
    @Query(value = "SELECT NEXTVAL(:seqName)", nativeQuery = true)
    Long nextSequenceValue(@Param("seqName") String seqName);
}

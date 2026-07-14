package com.multi.finance.repository;

import com.multi.finance.entity.CollectionNote;
import com.multi.finance.enums.CollectionNoteStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CollectionNoteRepository extends JpaRepository<CollectionNote, Long> {

    List<CollectionNote> findByStatus(CollectionNoteStatus status);

    List<CollectionNote> findByCollectedById(Long userId);

    List<CollectionNote> findByBillId(Long billId);

    List<CollectionNote> findByBillIdAndStatus(Long billId, CollectionNoteStatus status);

    void deleteBySourceEntryId(Long sourceEntryId);
}
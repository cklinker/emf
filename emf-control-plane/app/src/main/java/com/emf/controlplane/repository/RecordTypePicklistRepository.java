package com.emf.controlplane.repository;

import com.emf.controlplane.entity.RecordTypePicklist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RecordTypePicklistRepository extends JpaRepository<RecordTypePicklist, String> {

    List<RecordTypePicklist> findByRecordTypeId(String recordTypeId);

    Optional<RecordTypePicklist> findByRecordTypeIdAndFieldId(String recordTypeId, String fieldId);

    void deleteByRecordTypeId(String recordTypeId);
}

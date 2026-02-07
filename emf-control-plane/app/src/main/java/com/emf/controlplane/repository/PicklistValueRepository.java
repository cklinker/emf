package com.emf.controlplane.repository;

import com.emf.controlplane.entity.PicklistValue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PicklistValueRepository extends JpaRepository<PicklistValue, String> {

    List<PicklistValue> findByPicklistSourceTypeAndPicklistSourceIdAndActiveTrueOrderBySortOrderAsc(
            String sourceType, String sourceId);

    List<PicklistValue> findByPicklistSourceTypeAndPicklistSourceId(
            String sourceType, String sourceId);

    boolean existsByPicklistSourceTypeAndPicklistSourceIdAndValue(
            String sourceType, String sourceId, String value);

    void deleteByPicklistSourceTypeAndPicklistSourceId(String sourceType, String sourceId);
}

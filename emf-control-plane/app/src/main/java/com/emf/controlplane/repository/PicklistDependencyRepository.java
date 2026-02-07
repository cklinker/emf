package com.emf.controlplane.repository;

import com.emf.controlplane.entity.Field;
import com.emf.controlplane.entity.PicklistDependency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PicklistDependencyRepository extends JpaRepository<PicklistDependency, String> {

    Optional<PicklistDependency> findByControllingFieldAndDependentField(
            Field controllingField, Field dependentField);

    List<PicklistDependency> findByControllingField(Field controllingField);

    List<PicklistDependency> findByDependentField(Field dependentField);
}

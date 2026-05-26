package com.kmercoders.nkap.group;

import com.kmercoders.nkap.appuser.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GroupRepository extends JpaRepository<Group, Long> {

    List<Group> findByBudgetsId(Long budgetId);

    Optional<Group> findByIdAndBudgetsId(Long groupId, Long budgetId);

    boolean existsByBudgets_AppUserAndIsDefaultTrue(AppUser appUser);
}
package com.kmercoders.nkap.category;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    @Query("SELECT c FROM Category c LEFT JOIN FETCH c.group WHERE c.group.id = :groupId")
    List<Category> findByGroupId(@Param("groupId") Long groupId);
}
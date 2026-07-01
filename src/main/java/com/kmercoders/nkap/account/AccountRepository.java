package com.kmercoders.nkap.account;

import java.util.List;

import com.kmercoders.nkap.appuser.AppUser;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, Long> {
    List<Account> findByAppUser(AppUser appUser);

    java.util.Optional<Account> findByIdAndAppUser(Long id, AppUser appUser);
}
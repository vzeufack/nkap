package com.kmercoders.nkap.appuser;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
   AppUser findByEmail(String email);
}

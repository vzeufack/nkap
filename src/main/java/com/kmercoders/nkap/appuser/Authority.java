package com.kmercoders.nkap.appuser;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

import org.springframework.security.core.GrantedAuthority;

@Entity
public class Authority implements GrantedAuthority{
   private static final long serialVersionUID = 1L;
   
   @Id
   @GeneratedValue
   private Long id;
   
   private String authorityName;
   
   @ManyToOne
   private AppUser appUser;

   public Long getId() {
      return id;
   }

   public void setId(Long id) {
      this.id = id;
   }

   public String getAuthority() {
      return authorityName;
   }

   public void setAuthority(String authority) {
      this.authorityName = authority;
   }

   public AppUser getAppUser() {
      return appUser;
   }

   public void setAppUser(AppUser appUser) {
      this.appUser = appUser;
   }
}

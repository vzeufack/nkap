package com.kmercoders.nkap.appuser;

import java.util.Set;

import org.springframework.security.core.userdetails.UserDetails;

public class SecurityUser extends AppUser implements UserDetails{
    private static final long serialVersionUID = 1L;
   
    public SecurityUser() {
        
    }
    
    public SecurityUser(AppUser user) {
        this.setId(user.getId());
        this.setPassword(user.getPassword());
        this.setEmail(user.getEmail());
        this.setAuthorities(user.getAuthorities());
    }

    @Override
    public Set<Authority> getAuthorities() {
        return super.getAuthorities();
    }
    
    @Override
    public String getUsername() {
        return super.getEmail();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

}

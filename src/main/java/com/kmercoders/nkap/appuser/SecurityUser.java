package com.kmercoders.nkap.appuser;

import java.util.Set;

public class SecurityUser extends AppUser{
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

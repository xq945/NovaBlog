package com.novablog.security;

import com.novablog.entity.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Getter
public class SecurityUserDetails implements UserDetails {

    private final Long userId;
    private final String username;
    private final String password;
    private final String status;
    private final boolean muted;
    private final List<String> roles;

    public SecurityUserDetails(User user, List<String> roles) {
        this.userId = user.getId();
        this.username = user.getUsername();
        this.password = user.getPassword();
        this.status = user.getStatus() == 1 ? "NORMAL" : "DISABLED";
        this.muted = user.getMuted() != null && user.getMuted() == 1;
        this.roles = roles;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream()
            .map(SimpleGrantedAuthority::new)
            .toList();
    }

    @Override
    public boolean isEnabled() {
        return "NORMAL".equals(status);
    }

    @Override
    public boolean isAccountNonLocked() {
        return "NORMAL".equals(status);
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }
}

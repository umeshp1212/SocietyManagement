package com.society.module.auth.security;

import com.society.module.auth.entity.Permission;
import com.society.module.auth.entity.Role;
import com.society.module.auth.entity.User;
import com.society.module.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        Set<GrantedAuthority> authorities = new HashSet<>();

        for (Role role : user.getRoles()) {
            // Add role as authority with ROLE_ prefix
            authorities.add(new SimpleGrantedAuthority("ROLE_" + role.getRoleName()));

            // Add each permission as an authority
            for (Permission permission : role.getPermissions()) {
                authorities.add(new SimpleGrantedAuthority(permission.getPermissionName()));
            }
        }

        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(),
                user.getIsActive(),  // enabled
                true,                // accountNonExpired
                true,                // credentialsNonExpired
                true,                // accountNonLocked
                authorities
        );
    }
}

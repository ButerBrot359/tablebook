package com.tablebook.organization;

import com.tablebook.auth.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MembershipRepository extends JpaRepository<Membership, Long> {
    List<Membership> findAllByUser(User user);

    Optional<Membership> findMembershipByUserAndOrganization(User user, Organization organization);

    boolean existsByUserAndOrganization(User user, Organization organization);
}

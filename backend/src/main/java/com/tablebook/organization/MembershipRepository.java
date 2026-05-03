package com.tablebook.organization;

import com.tablebook.auth.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface MembershipRepository extends JpaRepository<Membership, Long> {
    @Query("SELECT m FROM Membership m JOIN FETCH m.organization WHERE m.user = :user")
    List<Membership> findAllByUser(User user);

    Optional<Membership> findMembershipByUserAndOrganization(User user, Organization organization);

    boolean existsByUserAndOrganization(User user, Organization organization);
}

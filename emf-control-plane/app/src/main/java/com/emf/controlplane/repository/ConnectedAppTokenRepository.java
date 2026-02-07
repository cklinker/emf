package com.emf.controlplane.repository;

import com.emf.controlplane.entity.ConnectedAppToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConnectedAppTokenRepository extends JpaRepository<ConnectedAppToken, String> {

    List<ConnectedAppToken> findByConnectedAppIdAndRevokedFalseOrderByIssuedAtDesc(String connectedAppId);

    Optional<ConnectedAppToken> findByTokenHashAndRevokedFalse(String tokenHash);
}

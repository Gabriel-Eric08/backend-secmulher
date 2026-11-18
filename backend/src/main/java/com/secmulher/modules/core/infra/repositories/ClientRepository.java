package com.secmulher.modules.core.infra.repositories;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.secmulher.modules.core.infra.entities.Client;

public interface ClientRepository extends JpaRepository<Client, UUID> {

}

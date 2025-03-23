package Napas.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import Napas.entity.NapasMessage;

@Repository
public interface NapasMessageRepository extends JpaRepository<NapasMessage, Long> {
}


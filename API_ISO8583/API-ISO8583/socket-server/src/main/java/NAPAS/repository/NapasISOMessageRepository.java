package NAPAS.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import NAPAS.entity.NapasISOMessage;


@Repository
public interface NapasISOMessageRepository extends JpaRepository<NapasISOMessage, Long> {
}

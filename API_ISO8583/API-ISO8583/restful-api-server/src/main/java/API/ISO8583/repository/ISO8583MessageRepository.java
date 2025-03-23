package API.ISO8583.repository;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import API.ISO8583.entity.ISO8583Message;

@Repository
public interface ISO8583MessageRepository extends JpaRepository<ISO8583Message, Long> {
}


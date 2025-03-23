package socketserver.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import socketserver.entity.SocketMessage;


@Repository
public interface SocketMessageRepository extends JpaRepository<SocketMessage, Long> {
}


package restapi.repository;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import restapi.entity.RestApiMessage;

@Repository
public interface RestApiMessageRepository extends JpaRepository<RestApiMessage, Long> {
}


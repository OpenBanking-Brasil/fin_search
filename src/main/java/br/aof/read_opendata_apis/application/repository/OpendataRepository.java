package br.aof.read_opendata_apis.application.repository;
import br.aof.read_opendata_apis.application.dto.OpenDataDTO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OpendataRepository extends JpaRepository<OpenDataDTO, Long> {
}

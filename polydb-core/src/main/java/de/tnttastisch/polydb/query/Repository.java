package de.tnttastisch.polydb.query;

import java.util.List;
import java.util.Optional;

public interface Repository<T> {

    void save(T entity);

    Optional<T> findById(Object id);

    List<T> findAll();

    void delete(T entity);

    void deleteById(Object id);
}

package com.projektzitd.file;

import com.projektzitd.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FileRepository extends JpaRepository<FileEntity, Long> {

    List<FileEntity> findByOwner(User owner);

    /** Files owned by user OR shared with them (username appears in sharedWith). */
    @Query("SELECT f FROM FileEntity f WHERE f.owner = :owner " +
           "OR f.sharedWith LIKE %:username%")
    List<FileEntity> findAccessibleByUser(@Param("owner") User owner,
                                          @Param("username") String username);
}

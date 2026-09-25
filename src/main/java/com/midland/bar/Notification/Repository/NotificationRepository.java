package com.midland.bar.Notification.Repository;

import com.midland.bar.Notification.Model.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, String> {

    @Query("SELECT n FROM Notification n WHERE n.targetUserUID = :userUID ORDER BY n.notifiedAt DESC")
    List<Notification> findByTargetUser(@Param("userUID") String userUID, Pageable pageable);

    @Query("SELECT COUNT(n) FROM Notification n WHERE n.targetUserUID = :userUID AND n.isRead = false")
    long countUnread(@Param("userUID") String userUID);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.targetUserUID = :userUID AND n.isRead = false")
    void markAllAsRead(@Param("userUID") String userUID);
}

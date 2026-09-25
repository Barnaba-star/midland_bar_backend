package com.midland.bar.Notification.Service;

import com.midland.bar.Config.Security.LoggerUser;
import com.midland.bar.Notification.Model.Notification;
import com.midland.bar.Notification.Repository.NotificationRepository;
import com.midland.bar.Bar.Model.SalesOpened;
import com.midland.bar.Uaa.Model.User;
import com.midland.bar.Uaa.Repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    private static final int MAX_NOTIFICATIONS = 30;

    public void notify(
            String userUID,
            String titleKey,
            String messageKey,
            Map<String, Object> params,
            String icon,
            String route
    ) {
        if (userUID == null) {
            return;
        }

        Notification notification = new Notification();
        notification.setTargetUserUID(userUID);
        notification.setTitleKey(titleKey);
        notification.setMessageKey(messageKey);
        notification.setIcon(icon);
        notification.setRoute(route);

        if (params != null && !params.isEmpty()) {
            try {
                notification.setParamsJson(objectMapper.writeValueAsString(params));
            } catch (Exception e) {
                log.warn("Could not serialize notification params", e);
            }
        }

        notificationRepository.save(notification);
    }

    /**
     * Notifies everyone assigned to the sale's branch that a sale was
     * completed — this is the "mauzo mangapi" (how many sales) signal
     * the header notification bell is meant to surface.
     */
    public void notifySaleCompleted(SalesOpened sale) {

        if (sale.getBranchUid() == null) {
            return;
        }

        List<User> branchUsers = userRepository.findAllUsersWithBranchAndRoles(sale.getBranchUid());

        Map<String, Object> params = new HashMap<>();
        params.put("code", sale.getSalesCode());
        params.put("amount", sale.getPaidAmount());

        for (User user : branchUsers) {
            notify(
                    user.getUid(),
                    "NOTIFICATIONS.NEW_SALE_TITLE",
                    "NOTIFICATIONS.NEW_SALE_MESSAGE",
                    params,
                    "point_of_sale",
                    "/pos/barSales"
            );
        }
    }

    public List<Notification> findMyNotifications() {
        String userUID = LoggerUser.getUser().getUid();
        Pageable pageable = PageRequest.of(0, MAX_NOTIFICATIONS);
        return notificationRepository.findByTargetUser(userUID, pageable);
    }

    public long countUnread() {
        return notificationRepository.countUnread(LoggerUser.getUser().getUid());
    }

    public void markAsRead(String notificationUID) {

        Optional<Notification> optionalNotification = notificationRepository.findById(notificationUID);

        if (optionalNotification.isEmpty()) {
            return;
        }

        Notification notification = optionalNotification.get();
        if (!notification.getTargetUserUID().equals(LoggerUser.getUser().getUid())) {
            return;
        }

        notification.setIsRead(true);
        notificationRepository.save(notification);
    }

    @Transactional
    public void markAllAsRead() {
        notificationRepository.markAllAsRead(LoggerUser.getUser().getUid());
    }
}

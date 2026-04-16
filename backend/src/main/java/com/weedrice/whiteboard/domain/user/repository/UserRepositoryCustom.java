package com.weedrice.whiteboard.domain.user.repository;

import com.weedrice.whiteboard.domain.user.dto.UserAdminSearchCondition;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface UserRepositoryCustom {
    Page<User> searchUsers(String keyword, Pageable pageable);
    Page<User> searchUsersVisibleTo(String keyword, List<Long> blockedUserIds, Pageable pageable);

    Page<User> searchUsersForAdmin(String keyword, UserAdminSearchCondition condition, Pageable pageable);
}

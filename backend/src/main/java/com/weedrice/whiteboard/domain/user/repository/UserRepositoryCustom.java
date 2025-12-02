package com.weedrice.whiteboard.domain.user.repository;

import com.weedrice.whiteboard.domain.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserRepositoryCustom {
    Page<User> searchUsers(String keyword, Pageable pageable);
}

package com.usth.edu.vn.repository;

import com.usth.edu.vn.model.UserDetails;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Page;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Comparator;
import java.util.List;

@ApplicationScoped
public class UserDetailsRepository implements PanacheRepository<UserDetails> {

    @Inject
    UserRepository userRepository;

    private static final int PAGE_SIZE = 20;

    public List<UserDetails> findAllUsers() {
        return listAll();
    }

    public UserDetails getUserDetail(long user_id) {
      UserDetails userDetail = userRepository.findById(user_id).getUserDetail();
      UserDetails tempUserDetail = UserDetails
        .builder()
        .firstname(userDetail.getFirstname())
        .lastname(userDetail.getLastname())
        .email(userDetail.getEmail())
        .createTime(userDetail.getCreateTime())
        .build();
      return tempUserDetail;
    }

    public List<UserDetails> findPagingUsers(long pageNo) {
        return findAll()
                .page(Page.of(Math.toIntExact(pageNo), PAGE_SIZE))
                .list();
    }

    public List<UserDetails> searchUsers(String keyWord) {
      return streamAll().filter(userDetail -> 
          userDetail.getFirstname().startsWith(keyWord) ||
          userDetail.getLastname().startsWith(keyWord) ||
          userDetail.getEmail().startsWith(keyWord) ||
          userDetail.getUser().getUsername().startsWith(keyWord))
        .sorted(Comparator.comparing(UserDetails::getFirstname).thenComparing(UserDetails::getLastname))
        .toList();
    }
}

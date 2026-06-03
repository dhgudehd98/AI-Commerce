package com.sh.aicommerce.entity;


import jakarta.persistence.*;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_member_email", columnNames = "email")
        }
)
@NoArgsConstructor
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "member_id")
    private Long id;

    @OneToMany(mappedBy = "member")
    private List<Orders> orders = new ArrayList<>();

    @Column(nullable = false)
    private String email; // 실제 로그인 할 떄 사용하는 이메일

    @Column(nullable = false)
    private String passwd;

    @Column(nullable = false)
    private String nickName; // 별명

    private String zipCode;
    private String address;
    private String addressDetail;
}

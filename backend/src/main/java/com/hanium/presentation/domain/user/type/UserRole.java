// 역할: 계정 권한과 권한별 우선순위를 정의하는 enum 타입입니다.
package com.hanium.presentation.domain.user.type;

public enum UserRole {
    USER(1),
    MANAGER(2),
    ADMIN(3);

    private final int priority;

    // 함수 역할: UserRole 권한의 정렬 우선순위를 초기화합니다.
    UserRole(int priority) {
        this.priority = priority;
    }

    // 함수 역할: 권한이 높은 사용자부터 정렬할 때 사용할 우선순위를 반환합니다.
    public int priority() {
        return priority;
    }

    // 함수 역할: 운영 콘솔에 접근할 수 있는 권한인지 확인합니다.
    public boolean canAccessOperationConsole() {
        return this == ADMIN || this == MANAGER;
    }

    // 함수 역할: 사용자 추방 권한을 가진 최고 관리자 계정인지 확인합니다.
    public boolean canExpelUsers() {
        return this == ADMIN;
    }

    // 함수 역할: 추방 대상에서 보호해야 하는 운영 권한 계정인지 확인합니다.
    public boolean isProtectedOperationAccount() {
        return this == ADMIN || this == MANAGER;
    }
}

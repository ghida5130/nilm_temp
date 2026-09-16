package com.nilm.monitoring.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity @Table(name = "managers")
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Manager {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "auth_sub", nullable = false, unique = true)
    private String authSub;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String organization;

    @Column(name = "sound_enabled", nullable = false)
    private boolean soundEnabled = true;

    public Manager(String authSub, String name, String organization) {
        this.authSub = authSub;
        this.name = name;
        this.organization = organization;
    }

    public void changeSoundSetting(boolean enabled) {
        this.soundEnabled = enabled;
    }
}

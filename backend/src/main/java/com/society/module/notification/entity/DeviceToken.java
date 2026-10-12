package com.society.module.notification.entity;

import com.society.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * An FCM registration token for a member's device (browser/PWA install), used to send
 * web-push notifications such as a new visitor/delivery awaiting approval.
 *
 * <p>A token is linked to the member by their owner id (the member login's principal).
 * The same physical device re-registers the same token on each login; registration is
 * idempotent on the token value (see {@code DeviceTokenRepository}).</p>
 */
@Entity
@Table(name = "device_tokens",
        uniqueConstraints = @UniqueConstraint(name = "uk_device_token", columnNames = "token"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviceToken extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "device_token_id")
    private Long deviceTokenId;

    /** Owner id of the member who registered this device (from the member JWT). */
    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    /** The FCM registration token (opaque, device-specific). */
    @Column(name = "token", nullable = false, length = 512)
    private String token;

    /** Platform hint: WEB, ANDROID, IOS. Defaults to WEB for the PWA. */
    @Column(name = "platform", length = 20)
    @Builder.Default
    private String platform = "WEB";
}

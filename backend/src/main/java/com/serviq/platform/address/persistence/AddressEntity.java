package com.serviq.platform.address.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "serviq_customer_address")
public class AddressEntity {
    @Id
    @Column(name = "address_id", nullable = false)
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "address_label", length = 80)
    private String addressLabel;

    @Column(name = "address_line_1", nullable = false, length = 255)
    private String addressLine1;

    @Column(name = "address_line_2", length = 255)
    private String addressLine2;

    @Column(name = "city", nullable = false, length = 120)
    private String city;

    @Column(name = "region", length = 120)
    private String region;

    @Column(name = "postal_code", length = 30)
    private String postalCode;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "country_code", nullable = false, length = 2, columnDefinition = "char(2)")
    private String countryCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected AddressEntity() {}

    public AddressEntity(UUID customerId, String addressLabel, String addressLine1,
                         String addressLine2, String city, String region,
                         String postalCode, String countryCode) {
        this.id = UUID.randomUUID();
        this.customerId = customerId;
        this.addressLabel = addressLabel;
        this.addressLine1 = addressLine1;
        this.addressLine2 = addressLine2;
        this.city = city;
        this.region = region;
        this.postalCode = postalCode;
        this.countryCode = countryCode;
        this.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public UUID getId() { return id; }
    public UUID getCustomerId() { return customerId; }
    public String getAddressLabel() { return addressLabel; }
    public String getAddressLine1() { return addressLine1; }
    public String getAddressLine2() { return addressLine2; }
    public String getCity() { return city; }
    public String getRegion() { return region; }
    public String getPostalCode() { return postalCode; }
    public String getCountryCode() { return countryCode; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}

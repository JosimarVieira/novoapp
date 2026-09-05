package com.novoapp.identity.repository;

import com.novoapp.identity.entity.Household;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.UUID;

@ApplicationScoped
public class HouseholdRepository implements PanacheRepositoryBase<Household, UUID> {
}

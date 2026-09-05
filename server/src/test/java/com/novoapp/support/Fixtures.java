package com.novoapp.support;

import io.agroal.api.AgroalDataSource;
import io.quarkus.agroal.DataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Monta e inspeciona dado de teste pelo datasource administrativo.
 *
 * <p>Usa o superusuario de proposito: ele ignora RLS, que e justamente o que
 * permite montar dois households e depois conferir, de fora, o que cada papel
 * da aplicacao conseguiu ou nao conseguiu enxergar. Nenhum codigo de producao
 * usa esta conexao.
 */
@ApplicationScoped
public class Fixtures {

    @Inject
    @DataSource("admin")
    AgroalDataSource admin;

    public UUID insertHousehold(String name) {
        return insertReturningId("INSERT INTO household (name) VALUES (?) RETURNING id", name);
    }

    public UUID insertMember(String name, String phoneNumber) {
        return insertReturningId("INSERT INTO member (name, phone_number) VALUES (?, ?) RETURNING id",
                name, phoneNumber);
    }

    public UUID insertMembership(UUID householdId, UUID memberId, String role) {
        return insertReturningId(
                "INSERT INTO household_membership (household_id, member_id, role) VALUES (?, ?, ?) RETURNING id",
                householdId, memberId, role);
    }

    public UUID insertChannelIdentity(UUID memberId, String channel, String externalId, UUID activeHouseholdId) {
        return insertReturningId("""
                INSERT INTO channel_identity (member_id, channel, external_id, active_household_id, verified_at)
                VALUES (?, ?, ?, ?, now()) RETURNING id""", memberId, channel, externalId, activeHouseholdId);
    }

    /**
     * Categorias na mao: household novo nasce sem nenhuma (ADR-0013) e criar
     * categoria por chat e cenario @etapa2. E o mesmo caminho que o README
     * documenta pra validacao real na Etapa 1.
     */
    public UUID insertExpenseCategory(UUID householdId, String name) {
        return insertReturningId(
                "INSERT INTO category (household_id, name, kind) VALUES (?, ?, 'EXPENSE') RETURNING id",
                householdId, name);
    }

    public UUID insertWallet(UUID householdId) {
        return insertReturningId(
                "INSERT INTO account (household_id, name, type) VALUES (?, 'Carteira', 'WALLET') RETURNING id",
                householdId);
    }

    public UUID insertInvite(UUID householdId, UUID invitedByMemberId, String phoneNumber,
                             String token, String status, Instant expiresAt) {
        return insertReturningId("""
                INSERT INTO household_invite
                    (household_id, invited_by_member_id, phone_number, token, status, expires_at)
                VALUES (?, ?, ?, ?, ?, ?) RETURNING id""",
                householdId, invitedByMemberId, phoneNumber, token, status,
                java.sql.Timestamp.from(expiresAt));
    }

    public void setDefaultAccount(UUID householdId, UUID memberId, UUID accountId) {
        execute("UPDATE household_membership SET default_account_id = ? WHERE household_id = ? AND member_id = ?",
                accountId, householdId, memberId);
    }

    public List<List<Object>> query(String sql, Object... parameters) {
        try (Connection connection = admin.getConnection();
             PreparedStatement statement = prepare(connection, sql, parameters);
             ResultSet results = statement.executeQuery()) {
            List<List<Object>> rows = new ArrayList<>();
            int columns = results.getMetaData().getColumnCount();
            while (results.next()) {
                List<Object> row = new ArrayList<>();
                for (int column = 1; column <= columns; column++) {
                    row.add(results.getObject(column));
                }
                rows.add(row);
            }
            return rows;
        } catch (SQLException e) {
            throw new IllegalStateException("Consulta de fixture falhou: " + sql, e);
        }
    }

    public long count(String sql, Object... parameters) {
        return ((Number) query(sql, parameters).get(0).get(0)).longValue();
    }

    public void execute(String sql, Object... parameters) {
        try (Connection connection = admin.getConnection();
             PreparedStatement statement = prepare(connection, sql, parameters)) {
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Comando de fixture falhou: " + sql, e);
        }
    }

    /** Deixa o banco limpo entre cenarios, sem recriar o container. */
    public void truncateAll() {
        execute("""
                TRUNCATE transaction, category, account, inbound_message, onboarding_session,
                         household_invite, channel_identity, household_membership, member, household
                RESTART IDENTITY CASCADE""");
    }

    private UUID insertReturningId(String sql, Object... parameters) {
        List<List<Object>> rows = query(sql, parameters);
        return (UUID) rows.get(0).get(0);
    }

    private PreparedStatement prepare(Connection connection, String sql, Object... parameters) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(sql);
        for (int index = 0; index < parameters.length; index++) {
            statement.setObject(index + 1, parameters[index]);
        }
        return statement;
    }
}

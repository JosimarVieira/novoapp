package com.novoapp.acceptance;

import com.novoapp.identity.onboarding.OnboardingMessages;
import com.novoapp.support.Fixtures;
import io.cucumber.java.pt.Dado;
import io.cucumber.java.pt.E;
import io.cucumber.java.pt.Entao;
import io.cucumber.java.pt.Quando;
import jakarta.inject.Inject;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Passos de `vinculo-de-identidade.feature`, cenarios @etapa1. */
public class IdentityLinkSteps {

    @Inject
    AcceptanceWorld world;

    @Inject
    Fixtures fixtures;

    // ------------------------------------------------------------------
    // Primeiro contato
    // ------------------------------------------------------------------

    @Dado("^que o número da aplicação nunca recebeu mensagem de \"([^\"]*)\"$")
    public void neverHeardFrom(String phoneNumber) {
        // O banco e truncado antes de cada cenario: nao ha estado a montar.
        assertThat(fixtures.count("SELECT count(*) FROM channel_identity WHERE external_id = ?",
                world.externalIdFor(phoneNumber))).isZero();
    }

    @Quando("^\"([^\"]*)\" envia \"([^\"]*)\" para o número da aplicação$")
    public void sendsToTheApplication(String actor, String text) {
        world.send(actor, text);
    }

    @Entao("^a pessoa recebe uma breve explicação do que é a aplicação$")
    public void receivesShortExplanation() {
        assertThat(world.lastReplyToCurrentActor())
                .contains("registra as despesas, a lista de mercado e as tarefas");
    }

    @E("^a pessoa recebe uma pergunta perguntando se quer criar uma família nova$")
    public void receivesCreateHouseholdQuestion() {
        assertThat(world.lastReplyToCurrentActor()).contains("Quer criar uma família nova?");
    }

    @E("^nenhum household é criado ainda$")
    public void noHouseholdYet() {
        assertThat(fixtures.count("SELECT count(*) FROM household")).isZero();
    }

    // ------------------------------------------------------------------
    // Criacao self-service
    // ------------------------------------------------------------------

    @Dado("^que \"([^\"]*)\" recebeu a pergunta para criar uma família nova$")
    public void wasAskedToCreateHousehold(String actor) {
        world.send(actor, "/start");
        assertThat(world.lastReplyTo(actor)).contains("Quer criar uma família nova?");
    }

    @Quando("^a pessoa responde \"([^\"]*)\"$")
    public void personAnswers(String text) {
        world.send(world.currentActor, text);
    }

    @E("^a pessoa responde \"([^\"]*)\" para o nome da família$")
    public void personAnswersHouseholdName(String householdName) {
        world.send(world.currentActor, householdName);
    }

    @Entao("^um household \"([^\"]*)\" é criado$")
    public void householdWasCreated(String householdName) {
        List<List<Object>> rows = fixtures.query("SELECT id FROM household WHERE name = ?", householdName);
        assertThat(rows).hasSize(1);
        world.households.put(householdName, (UUID) rows.get(0).get(0));
    }

    @E("^a pessoa vira membro desse household com papel \"([^\"]*)\"$")
    public void personBecameMemberWithRole(String role) {
        assertThat(roleOfCurrentActorIn(world.households.values().iterator().next())).isEqualTo(role);
    }

    @E("^o \"channel_identity\" da pessoa fica com \"active_household_id\" apontando pro household \"([^\"]*)\"$")
    public void activeHouseholdPointsTo(String householdName) {
        assertThat(activeHouseholdOf(world.currentActor)).isEqualTo(world.households.get(householdName));
    }

    @E("^o household \"([^\"]*)\" ganha uma conta do tipo carteira implícita$")
    public void householdGotImplicitWallet(String householdName) {
        assertThat(fixtures.count("SELECT count(*) FROM account WHERE household_id = ? AND type = 'WALLET'",
                world.households.get(householdName))).isEqualTo(1);
    }

    @E("^a pessoa recebe uma pergunta se prefere continuar tudo pelo chat ou terminar a configuração no aplicativo$")
    public void receivesSetupChannelQuestion() {
        assertThat(world.lastReplyToCurrentActor())
                .contains("pelo chat, ou prefere pelo aplicativo?");
    }

    @Dado("^que \"([^\"]*)\" acabou de criar o household \"([^\"]*)\"$")
    public void justCreatedHousehold(String actor, String householdName) {
        world.send(actor, "/start");
        world.send(actor, "sim");
        world.send(actor, householdName);
        householdWasCreated(householdName);
    }

    @Entao("^a pessoa recebe um aviso de que o aplicativo web ainda não está disponível nesta etapa$")
    public void receivesAppNotAvailableWarning() {
        assertThat(world.lastReplyToCurrentActor()).contains("ainda não está disponível nesta etapa");
    }

    @E("^a pessoa recebe a opção de continuar a configuração pelo chat$")
    public void receivesOptionToContinueByChat() {
        assertThat(world.lastReplyToCurrentActor()).contains("Quer continuar a configuração por aqui?");
    }

    // ------------------------------------------------------------------
    // Convite (ADR-0020)
    // ------------------------------------------------------------------

    @Dado("^que existe um convite \"([^\"]*)\" do household \"([^\"]*)\" para o telefone \"([^\"]*)\"$")
    public void inviteExists(String status, String householdName, String phoneNumber) {
        createInvite(householdName, phoneNumber, status, Instant.now().plus(7, ChronoUnit.DAYS));
    }

    @Dado("^que existe um convite do household \"([^\"]*)\" para o telefone \"([^\"]*)\" criado há (\\d+) dias$")
    public void inviteCreatedDaysAgo(String householdName, String phoneNumber, int days) {
        // Status continua PENDING no banco: EXPIRED e calculado a partir de
        // expires_at, sem job (ADR-0020, mesmo padrao do ADR-0014).
        createInvite(householdName, phoneNumber, "PENDING",
                Instant.now().minus(days - 7L, ChronoUnit.DAYS));
    }

    @Quando("^\"([^\"]*)\" abre o link do convite e envia \"/start\"$")
    public void opensInviteLink(String actor) {
        world.send(actor, "/start " + onlyInviteToken());
    }

    @Quando("^\"([^\"]*)\" abre o link do convite novamente$")
    public void opensInviteLinkAgain(String actor) {
        world.send(actor, "/start " + onlyInviteToken());
    }

    @Quando("^\"([^\"]*)\" abre o link do convite e compartilha o contato \"([^\"]*)\"$")
    public void opensInviteLinkAndSharesContact(String actor, String phoneNumber) {
        world.send(actor, "/start " + onlyInviteToken());
        world.shareContact(actor, phoneNumber);
    }

    @Entao("^a pessoa recebe um pedido para compartilhar o contato$")
    public void receivesContactRequest() {
        assertThat(world.lastReplyToCurrentActor()).contains("compartilhe o seu contato");
    }

    @Quando("^a pessoa compartilha o contato \"([^\"]*)\"$")
    public void sharesContact(String phoneNumber) {
        world.shareContact(world.currentActor, phoneNumber);
    }

    @Entao("^a pessoa vira membro do household \"([^\"]*)\" com papel \"([^\"]*)\"$")
    public void becameMemberOf(String householdName, String role) {
        assertThat(roleOfCurrentActorIn(world.households.get(householdName))).isEqualTo(role);
    }

    @E("^o convite fica com status \"([^\"]*)\"$")
    public void inviteHasStatus(String status) {
        assertThat(fixtures.query("SELECT status FROM household_invite").get(0).get(0)).isEqualTo(status);
    }

    @E("^o convite continua com status \"([^\"]*)\"$")
    public void inviteStillHasStatus(String status) {
        inviteHasStatus(status);
    }

    /**
     * EXPIRED nunca e gravado: e derivado de <code>expires_at</code>. Este passo
     * confere a situacao observavel, que e o que o cenario descreve.
     */
    @E("^o convite tem status \"EXPIRED\"$")
    public void inviteIsExpired() {
        List<List<Object>> rows = fixtures.query("SELECT status, expires_at FROM household_invite");
        assertThat(rows.get(0).get(0)).isEqualTo("PENDING");
        assertThat(((java.sql.Timestamp) rows.get(0).get(1)).toInstant()).isBefore(Instant.now());
    }

    @E("^a pessoa recebe a confirmação de entrada na família$")
    public void receivesJoinConfirmation() {
        assertThat(world.lastReplyToCurrentActor()).contains("você agora faz parte da família");
    }

    @Entao("^a pessoa recebe um aviso de que este convite não é para o número dela$")
    public void receivesPhoneMismatchWarning() {
        assertThat(world.lastReplyToCurrentActor()).isEqualTo(OnboardingMessages.INVITE_PHONE_MISMATCH);
    }

    @Entao("^a pessoa recebe um aviso de que o convite expirou$")
    public void receivesExpiredWarning() {
        assertThat(world.repliesTo(world.currentActor))
                .extracting(reply -> reply.text())
                .contains(OnboardingMessages.INVITE_EXPIRED);
    }

    @Entao("^a pessoa recebe um aviso de que o convite já foi usado$")
    public void receivesAlreadyUsedWarning() {
        assertThat(world.lastReplyToCurrentActor()).isEqualTo(OnboardingMessages.INVITE_ALREADY_USED);
    }

    /**
     * "Nenhum vinculo" e sobre a pessoa que tentou entrar, nao sobre a familia:
     * o household do convite ja tem o OWNER que emitiu o convite.
     */
    @E("^nenhum vínculo é criado$")
    public void noMembershipCreated() {
        assertThat(fixtures.count("""
                SELECT count(*) FROM household_membership m
                JOIN channel_identity ci ON ci.member_id = m.member_id
                WHERE ci.external_id = ?""", world.externalIdFor(world.currentActor))).isZero();
    }

    @E("^nenhum vínculo novo é criado$")
    public void noNewMembershipCreated() {
        noMembershipCreated();
    }

    // ------------------------------------------------------------------
    // Sem convite
    // ------------------------------------------------------------------

    @E("^que \"([^\"]*)\" nunca recebeu convite de nenhum household$")
    public void neverInvited(String phoneNumber) {
        assertThat(fixtures.count("SELECT count(*) FROM household_invite WHERE phone_number = ?", phoneNumber))
                .isZero();
    }

    @Entao("^a pessoa recebe um aviso de que só é possível entrar mediante convite$")
    public void receivesInviteOnlyWarning() {
        assertThat(world.lastReplyToCurrentActor()).contains("só é possível por convite");
    }

    @E("^a pessoa recebe a opção de criar a própria família em vez disso$")
    public void receivesOptionToCreateOwnHousehold() {
        assertThat(world.lastReplyToCurrentActor()).contains("Quer criar uma família nova?");
    }

    // ------------------------------------------------------------------
    // Pessoa em mais de um household (ADR-0007)
    // ------------------------------------------------------------------

    @Dado("^que \"([^\"]*)\" já é membro do household \"([^\"]*)\" com o telefone \"([^\"]*)\"$")
    public void alreadyMemberOf(String memberName, String householdName, String phoneNumber) {
        world.alias(memberName, phoneNumber);
        world.nameOf(phoneNumber, memberName);

        UUID householdId = fixtures.insertHousehold(householdName);
        fixtures.insertWallet(householdId);
        UUID memberId = fixtures.insertMember(memberName, phoneNumber);
        fixtures.insertMembership(householdId, memberId, "OWNER");
        fixtures.insertChannelIdentity(memberId, "TELEGRAM", world.externalIdFor(phoneNumber), householdId);

        world.households.put(householdName, householdId);
        world.members.put(memberName, memberId);
    }

    @Entao("^\"([^\"]*)\" vira membro do household \"([^\"]*)\" também, reaproveitando a mesma pessoa$")
    public void becameMemberReusingSamePerson(String memberName, String householdName) {
        // Uma pessoa so, com dois vinculos -- e nao duas pessoas homonimas (ADR-0007).
        assertThat(fixtures.count("SELECT count(*) FROM member WHERE name = ?", memberName)).isEqualTo(1);
        assertThat(fixtures.count("SELECT count(*) FROM household_membership WHERE member_id = ?",
                world.members.get(memberName))).isEqualTo(2);
        assertThat(roleOfCurrentActorIn(world.households.get(householdName))).isEqualTo("MEMBER");
    }

    @E("^o \"active_household_id\" de \"([^\"]*)\" continua apontando pro household \"([^\"]*)\"$")
    public void activeHouseholdUnchanged(String memberName, String householdName) {
        assertThat(activeHouseholdOf(memberName)).isEqualTo(world.households.get(householdName));
    }

    @E("^\"([^\"]*)\" recebe a confirmação de entrada na família \"([^\"]*)\", com a informação de como trocar de família ativa$")
    public void receivesJoinConfirmationWithSwitchHint(String memberName, String householdName) {
        assertThat(world.lastReplyTo(memberName))
                .contains("faz parte da família \"" + householdName + "\"")
                .contains("Para trocar, diga: usar " + householdName);
    }

    // ------------------------------------------------------------------

    private void createInvite(String householdName, String phoneNumber, String status, Instant expiresAt) {
        UUID householdId = world.households.computeIfAbsent(householdName, name -> {
            UUID created = fixtures.insertHousehold(name);
            fixtures.insertWallet(created);
            return created;
        });
        UUID owner = world.members.computeIfAbsent("__owner__:" + householdName, key -> {
            UUID created = fixtures.insertMember("Ana", null);
            fixtures.insertMembership(householdId, created, "OWNER");
            return created;
        });
        String token = "convite-" + UUID.randomUUID();
        fixtures.insertInvite(householdId, owner, phoneNumber, token, status, expiresAt);
        world.inviteTokens.put(householdName, token);
    }

    private String onlyInviteToken() {
        return world.inviteTokens.values().iterator().next();
    }

    private String roleOfCurrentActorIn(UUID householdId) {
        List<List<Object>> rows = fixtures.query("""
                SELECT m.role FROM household_membership m
                JOIN channel_identity ci ON ci.member_id = m.member_id
                WHERE ci.external_id = ? AND m.household_id = ?""",
                world.externalIdFor(world.currentActor), householdId);
        assertThat(rows).hasSize(1);
        return (String) rows.get(0).get(0);
    }

    private UUID activeHouseholdOf(String actor) {
        List<List<Object>> rows = fixtures.query(
                "SELECT active_household_id FROM channel_identity WHERE external_id = ?",
                world.externalIdFor(actor));
        assertThat(rows).hasSize(1);
        return (UUID) rows.get(0).get(0);
    }
}

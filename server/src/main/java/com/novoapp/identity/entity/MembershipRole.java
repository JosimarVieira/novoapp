package com.novoapp.identity.entity;

/**
 * Papel do membro dentro de um household. E por vinculo, nao da pessoa em geral:
 * o mesmo membro pode ser OWNER numa familia e MEMBER noutra (ADR-0007).
 */
public enum MembershipRole {
    OWNER,
    MEMBER
}

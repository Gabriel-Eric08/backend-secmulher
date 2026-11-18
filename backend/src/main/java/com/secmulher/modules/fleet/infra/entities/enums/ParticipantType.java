package com.secmulher.modules.fleet.infra.entities.enums;

// Quem é o participante de uma viagem
public enum ParticipantType {
    USER,       // usuário do sistema (servidora)
    CLIENT,     // atendida cadastrada em clients
    EXTERNAL    // pessoa sem cadastro: nome, telefone e documento informados na viagem
}

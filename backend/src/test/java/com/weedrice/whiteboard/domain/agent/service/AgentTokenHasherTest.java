package com.weedrice.whiteboard.domain.agent.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentTokenHasherTest {

    @Test
    void preservesLowercaseSha256TokenStorageFormat() {
        assertThat(AgentTokenHasher.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}

package com.dravorax.casinodx.economy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EconomyProviderModeTest {

    @Test
    void parsesKnownProviderAliases() {
        assertEquals(EconomyProviderModes.Mode.AUTO, EconomyProviderModes.parse("auto"));
        assertEquals(EconomyProviderModes.Mode.AUTO, EconomyProviderModes.parse(null));
        assertEquals(EconomyProviderModes.Mode.AUTO, EconomyProviderModes.parse(" "));
        assertEquals(EconomyProviderModes.Mode.VAULT, EconomyProviderModes.parse("vault"));
        assertEquals(EconomyProviderModes.Mode.VAULT, EconomyProviderModes.parse("Vault"));
        assertEquals(EconomyProviderModes.Mode.VAULTUNLOCKED,
                EconomyProviderModes.parse("vaultunlocked"));
        assertEquals(EconomyProviderModes.Mode.VAULTUNLOCKED,
                EconomyProviderModes.parse("vault-unlocked"));
        assertEquals(EconomyProviderModes.Mode.VAULTUNLOCKED,
                EconomyProviderModes.parse("vault2"));
        assertEquals(EconomyProviderModes.Mode.AUTO, EconomyProviderModes.parse("nonsense"));
    }
}

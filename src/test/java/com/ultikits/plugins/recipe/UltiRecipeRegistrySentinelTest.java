package com.ultikits.plugins.recipe;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Reopen guard for UltiRecipe's test-time Bukkit registry bootstrap.
 * <p>
 * Every assertion here depends on a live server, never a bare registry constant: {@code
 * mockbukkit-v1.21} registers its {@code RegistryAccess} mock through {@code ServiceLoader},
 * so a bare registry constant would resolve merely from the dependency being on the classpath,
 * independent of whether {@link org.mockbukkit.mockbukkit.MockBukkit#mock()} was ever called.
 * If this class is ever edited to remove the {@code @BeforeEach}/{@code @AfterEach} lifecycle
 * pair, every test below must go red.
 */
class UltiRecipeRegistrySentinelTest {

    @Test
    void liveServerIsBootstrapped() {
        assertNotNull(Bukkit.getServer(), "live server bootstrap must be present");
    }

    @Test
    void unsafeValuesResolves() {
        assertNotNull(Bukkit.getUnsafe(), "UnsafeValues must resolve on a live server");
    }

    @Test
    void createProfileDoesNotSilentlyReturnNull() {
        Object profile = Bukkit.createProfile(UUID.randomUUID(), "SentinelPlayer");
        assertNotNull(profile, "createProfile must not silently return null");
    }

    @Test
    void itemStackConstructionResolvesRegistry() {
        ItemStack stack = new ItemStack(Material.DIAMOND);
        assertNotNull(stack);
        assertEquals(Material.DIAMOND, stack.getType());
    }
}

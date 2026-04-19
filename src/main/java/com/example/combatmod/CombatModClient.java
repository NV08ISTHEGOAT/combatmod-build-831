package com.example.combatmod;
import com.example.combatmod.gui.CombatMenuScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.text.Text;
import eu.pb4.placeholders.api.Placeholders;
import eu.pb4.placeholders.api.PlaceholderResult;
public class CombatModClient implements ClientModInitializer {
    public static final String MOD_ID = "combatmod";
    public static final Identifier REACH_MODIFIER_ID = Identifier.of(MOD_ID, "reach_modifier");
    private static KeyBinding menuKey;
    @Override
    public void onInitializeClient() {
        menuKey = createSafeKeyBinding("key.combatmod.open_menu", GLFW.GLFW_KEY_R, "category.combatmod.general");
        
        Placeholders.registerCommon(Identifier.of(MOD_ID, "reach"), (ctx, arg) -> PlaceholderResult.value(ModConfig.reachEnabled ? "Enabled" : "Disabled"));
        Placeholders.registerCommon(Identifier.of(MOD_ID, "reveal"), (ctx, arg) -> PlaceholderResult.value(ModConfig.invisRevealEnabled ? "Enabled" : "Disabled"));
        Placeholders.registerCommon(Identifier.of(MOD_ID, "reach_distance"), (ctx, arg) -> PlaceholderResult.value(String.format("%.1f", ModConfig.reachDistance)));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (menuKey != null) {
                while (menuKey.wasPressed()) {
                    if (client.currentScreen == null) client.setScreen(new CombatMenuScreen());
                }
            }
            if (client.player != null && client.world != null) applyReachModifier(client.player);
        });
    }
    private static KeyBinding createSafeKeyBinding(String id, int code, String categoryId) {
        System.out.println("[CombatMod] Initializing keybinding: " + id);
        try {
            Object categoryObj = null;
            try {
                Method regCat = KeyBinding.class.getDeclaredMethod("registerCategory", Identifier.class);
                regCat.setAccessible(true);
                categoryObj = regCat.invoke(null, Identifier.of(MOD_ID, "general_cat"));
            } catch (Exception e) {
                try {
                    // Alternative for older mapped versions
                    for (Method m : KeyBinding.class.getDeclaredMethods()) {
                        if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == Identifier.class && m.getReturnType().getName().contains("Category")) {
                            categoryObj = m.invoke(null, Identifier.of(MOD_ID, "general_cat"));
                            break;
                        }
                    }
                } catch (Exception e2) {}
            }

            for (Constructor<?> c : KeyBinding.class.getDeclaredConstructors()) {
                c.setAccessible(true);
                Class<?>[] p = c.getParameterTypes();
                if (p.length == 4) {
                    try {
                        Object label = (p[0] == String.class) ? id : Text.translatable(id);
                        Object type = InputUtil.Type.KEYSYM;
                        Object key = (p[2] == int.class) ? code : InputUtil.fromKeyCode(code, -1);
                        Object cat = (categoryObj != null && p[3].isAssignableFrom(categoryObj.getClass())) ? categoryObj : categoryId;
                        
                        KeyBinding kb = (KeyBinding) c.newInstance(label, type, key, cat);
                        KeyBinding registered = KeyBindingHelper.registerKeyBinding(kb);
                        System.out.println("[CombatMod] Successfully registered keybinding!");
                        return registered;
                    } catch (Exception ex) {
                        System.out.println("[CombatMod] Trying next constructor...");
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("[CombatMod] ERROR: Could not find a valid KeyBinding constructor.");
        return null;
    }
    public static void applyReachModifier(PlayerEntity player) {
        EntityAttributeInstance blockRange = getAttr(player, "BLOCK_INTERACTION_RANGE", "PLAYER_BLOCK_INTERACTION_RANGE");
        EntityAttributeInstance entityRange = getAttr(player, "ENTITY_INTERACTION_RANGE", "PLAYER_ENTITY_INTERACTION_RANGE");
        if (blockRange == null || entityRange == null) return;
        blockRange.removeModifier(REACH_MODIFIER_ID);
        entityRange.removeModifier(REACH_MODIFIER_ID);
        if (ModConfig.reachEnabled) {
            blockRange.addTemporaryModifier(new EntityAttributeModifier(REACH_MODIFIER_ID,
                ModConfig.reachDistance - blockRange.getBaseValue(), EntityAttributeModifier.Operation.ADD_VALUE));
            entityRange.addTemporaryModifier(new EntityAttributeModifier(REACH_MODIFIER_ID,
                ModConfig.reachDistance - entityRange.getBaseValue(), EntityAttributeModifier.Operation.ADD_VALUE));
        }
    }
    private static EntityAttributeInstance getAttr(PlayerEntity p, String... names) {
        for (String name : names) {
            try {
                Field f = EntityAttributes.class.getField(name);
                Object attr = f.get(null);
                if (attr == null) continue;
                for (Method m : p.getClass().getMethods()) {
                    if (m.getName().startsWith("getAttributeInstance") || m.getName().equals("method_6017")) {
                        if (m.getParameterCount() == 1 && m.getParameterTypes()[0].isAssignableFrom(attr.getClass())) {
                            return (EntityAttributeInstance) m.invoke(p, attr);
                        }
                    }
                }
            } catch (Exception e) {}
        }
        return null;
    }
}

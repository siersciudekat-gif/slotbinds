package pl.slotbinds;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;

/** SlotBinds - widget HUD z bindami slotów 1-9. F12 otwiera menu pozycji i rozmiaru. */
public class SlotBindsMod implements ClientModInitializer {
    private static KeyBinding openMenu;

    @Override
    public void onInitializeClient() {
        openMenu = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.slotbinds.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F12, "key.categories.slotbinds"));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (openMenu.wasPressed()) {
                if (mc.currentScreen == null) mc.setScreen(new ConfigScreen());
            }
        });

        HudRenderCallback.EVENT.register((ctx, tickCounter) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.currentScreen instanceof ConfigScreen) return; // podgląd rysuje sam ekran
            Config c = Config.INSTANCE;
            Widget.render(ctx, c.x, c.y, c.scale);
        });
    }

    // ------------------------------------------------------------ konfiguracja
    public static class Config {
        public int x = 10;
        public int y = 10;
        public float scale = 1.0f;

        private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
        private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("slotbinds.json");
        public static Config INSTANCE = load();

        private static Config load() {
            try {
                if (Files.exists(PATH)) return GSON.fromJson(Files.readString(PATH), Config.class);
            } catch (Exception ignored) {}
            return new Config();
        }

        public static void save() {
            try { Files.writeString(PATH, GSON.toJson(INSTANCE)); } catch (Exception ignored) {}
        }

        public static void reset() {
            INSTANCE = new Config();
            save();
        }
    }

    // ------------------------------------------------------------ widget HUD
    public static class Widget {
        public static final int CELL_W = 20, GAP = 2, CELL_H = 28;
        public static final int WIDTH = 9 * CELL_W + 8 * GAP;
        public static final int HEIGHT = CELL_H;

        public static void render(DrawContext ctx, int x, int y, float scale) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null) return;
            int selected = mc.player.getInventory().selectedSlot;

            MatrixStack m = ctx.getMatrices();
            m.push();
            m.translate(x, y, 0);
            m.scale(scale, scale, 1f);

            for (int i = 0; i < 9; i++) {
                int sx = i * (CELL_W + GAP);
                int border = i == selected ? 0xFFFFFFFF : 0xFF555555;
                ctx.fill(sx, 0, sx + CELL_W, CELL_H, 0x99000000);
                ctx.fill(sx, 0, sx + CELL_W, 1, border);
                ctx.fill(sx, CELL_H - 1, sx + CELL_W, CELL_H, border);
                ctx.fill(sx, 0, sx + 1, CELL_H, border);
                ctx.fill(sx + CELL_W - 1, 0, sx + CELL_W, CELL_H, border);

                // nazwa klawisza przypisanego do slotu (uwzględnia zmiany w sterowaniu)
                String key = mc.options.hotbarKeys[i].getBoundKeyLocalizedText().getString();
                int tw = mc.textRenderer.getWidth(key);
                ctx.drawText(mc.textRenderer, key, sx + (CELL_W - tw) / 2, 2, 0xFFFFFFFF, true);

                ItemStack stack = mc.player.getInventory().getStack(i);
                ctx.drawItem(stack, sx + 2, 11);
            }
            m.pop();
        }
    }

    // ------------------------------------------------------------ menu pod F12
    public static class ConfigScreen extends Screen {
        private static final float MIN = 0.5f, MAX = 3.0f;
        private boolean dragging;
        private int dragOffX, dragOffY;

        public ConfigScreen() {
            super(Text.literal("SlotBinds"));
        }

        @Override
        protected void init() {
            Config cfg = Config.INSTANCE;

            addDrawableChild(new SliderWidget(width / 2 - 100, height - 56, 200, 20,
                    Text.literal("Rozmiar: " + (int) (cfg.scale * 100) + "%"),
                    (cfg.scale - MIN) / (MAX - MIN)) {
                @Override protected void updateMessage() {
                    setMessage(Text.literal("Rozmiar: " + (int) (Config.INSTANCE.scale * 100) + "%"));
                }
                @Override protected void applyValue() {
                    Config.INSTANCE.scale = (float) (MIN + value * (MAX - MIN));
                }
            });

            addDrawableChild(ButtonWidget.builder(Text.literal("Resetuj"), b -> {
                Config.reset();
                client.setScreen(new ConfigScreen());
            }).dimensions(width / 2 - 100, height - 30, 98, 20).build());

            addDrawableChild(ButtonWidget.builder(Text.literal("Gotowe"), b -> close())
                    .dimensions(width / 2 + 2, height - 30, 98, 20).build());
        }

        @Override
        public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
            super.render(ctx, mouseX, mouseY, delta);
            Config cfg = Config.INSTANCE;

            int w = (int) (Widget.WIDTH * cfg.scale), h = (int) (Widget.HEIGHT * cfg.scale);
            ctx.fill(cfg.x - 2, cfg.y - 2, cfg.x + w + 2, cfg.y + h + 2, 0x4400AAFF);
            Widget.render(ctx, cfg.x, cfg.y, cfg.scale);

            ctx.drawCenteredTextWithShadow(textRenderer, "Przeciągnij widget myszką, aby zmienić jego pozycję",
                    width / 2, 10, 0xFFFFFFFF);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (super.mouseClicked(mx, my, button)) return true;
            Config cfg = Config.INSTANCE;
            int w = (int) (Widget.WIDTH * cfg.scale), h = (int) (Widget.HEIGHT * cfg.scale);
            if (button == 0 && mx >= cfg.x && mx <= cfg.x + w && my >= cfg.y && my <= cfg.y + h) {
                dragging = true;
                dragOffX = (int) mx - cfg.x;
                dragOffY = (int) my - cfg.y;
                return true;
            }
            return false;
        }

        @Override
        public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
            if (dragging) {
                Config cfg = Config.INSTANCE;
                int w = (int) (Widget.WIDTH * cfg.scale), h = (int) (Widget.HEIGHT * cfg.scale);
                cfg.x = Math.max(0, Math.min(width - w, (int) mx - dragOffX));
                cfg.y = Math.max(0, Math.min(height - h, (int) my - dragOffY));
                return true;
            }
            return super.mouseDragged(mx, my, button, dx, dy);
        }

        @Override
        public boolean mouseReleased(double mx, double my, int button) {
            dragging = false;
            return super.mouseReleased(mx, my, button);
        }

        @Override
        public void close() {
            Config.save();
            super.close();
        }
    }
}

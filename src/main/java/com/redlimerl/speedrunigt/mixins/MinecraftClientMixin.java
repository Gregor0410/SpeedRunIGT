package com.redlimerl.speedrunigt.mixins;

import com.redlimerl.speedrunigt.option.SpeedRunOptions;
import com.redlimerl.speedrunigt.option.TimerPosition;
import com.redlimerl.speedrunigt.timer.InGameTimer;
import com.redlimerl.speedrunigt.timer.RunCategory;
import com.redlimerl.speedrunigt.timer.TimerStatus;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.SaveLevelScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.Window;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.LiteralText;
import net.minecraft.text.MutableText;
import net.minecraft.util.Formatting;
import net.minecraft.util.registry.DynamicRegistryManager;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.level.LevelInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;
import java.util.Objects;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {

    @Shadow public abstract boolean isInSingleplayer();

    @Shadow @Final public GameOptions options;
    @Shadow @Final public TextRenderer textRenderer;

    @Shadow public abstract boolean isPaused();

    @Shadow @Nullable public Screen currentScreen;
    @Shadow @Final private Window window;
    private static String lastWorldName = null;
    private static boolean lastWorldOpen = false;

    private @NotNull
    final InGameTimer timer = InGameTimer.INSTANCE;

    @Inject(at = @At("HEAD"), method = "createWorld")
    public void onCreate(String worldName, LevelInfo levelInfo, DynamicRegistryManager.Impl registryTracker, GeneratorOptions generatorOptions, CallbackInfo ci) {
        if (timer.getStatus() != TimerStatus.NONE) {
            timer.end();
        }
        lastWorldName = worldName;
        lastWorldOpen = true;
    }

    @Inject(at = @At("HEAD"), method = "startIntegratedServer(Ljava/lang/String;)V")
    public void onWorldOpen(String worldName, CallbackInfo ci) {
        lastWorldOpen = worldName.equals(lastWorldName);
        if (!lastWorldOpen) {
            timer.end();
        }
    }

    @Inject(at = @At("HEAD"), method = "joinWorld")
    public void onJoin(ClientWorld world, CallbackInfo ci) {
        if (!isInSingleplayer()) return;

        if (this.timer.getStatus() == TimerStatus.NONE && lastWorldOpen) {
            this.timer.start();
        } else if (lastWorldOpen) {
            this.timer.setPause(true, TimerStatus.IDLE);
        }

        //Enter Nether
        if (timer.getCategory() == RunCategory.ENTER_NETHER && Objects.equals(world.getRegistryKey().getValue().toString(), DimensionType.THE_NETHER_REGISTRY_KEY.toString())) {
            timer.complete();
        }

        //Enter End
        if (timer.getCategory() == RunCategory.ENTER_END && Objects.equals(world.getRegistryKey().getValue().toString(), DimensionType.THE_END_REGISTRY_KEY.toString())) {
            timer.complete();
        }
    }

    @Inject(at = @At("HEAD"), method = "disconnect(Lnet/minecraft/client/gui/screen/Screen;)V")
    public void onDisconnect(Screen screen, CallbackInfo ci) {
        if (this.timer.getStatus() != TimerStatus.NONE && screen instanceof SaveLevelScreen) {
            if (this.timer.getStatus() == TimerStatus.COMPLETED) {
                this.timer.end();
            } else {
                this.timer.setPause(true, TimerStatus.LEAVE);
            }
        }
    }

    @ModifyVariable(method = "render(Z)V", at = @At(value = "STORE"), ordinal = 1)
    private boolean renderMixin(boolean paused) {
        if (this.timer.getStatus() == TimerStatus.RUNNING && paused) {
            this.timer.setPause(true, TimerStatus.PAUSED);
        } else if (this.timer.getStatus() == TimerStatus.PAUSED && !paused) {
            this.timer.setPause(false);
        }

        return paused;
    }

    @Inject(method = "handleInputEvents", at = @At(value = "HEAD"))
    private void slotChange(CallbackInfo ci) {
        GameOptions o = this.options;
        if (timer.getStatus() == TimerStatus.IDLE) {
            if (o.keyAttack.isPressed() || o.keyDrop.isPressed() || o.keyInventory.isPressed() || o.keySneak.wasPressed() || o.keySwapHands.isPressed()
                    || o.keyUse.isPressed() || o.keyPickItem.isPressed() || o.keySprint.wasPressed() || Arrays.stream(o.keysHotbar).anyMatch(KeyBinding::isPressed)) {
                timer.setPause(false);
            }
        }
    }

    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/toast/ToastManager;draw(Lnet/minecraft/client/util/math/MatrixStack;)V", shift = At.Shift.AFTER))
    private void drawTimer(CallbackInfo ci) {
        if (!this.options.hudHidden && this.isInSingleplayer()) {
            MatrixStack matrixStack = new MatrixStack();
            TimerPosition pos = SpeedRunOptions.getOption(SpeedRunOptions.TIMER_POS);
            if (pos != TimerPosition.NONE && timer.getStatus() != TimerStatus.NONE) {
                int x = 12, y = window.getScaledHeight() - 32;
                MutableText igt = new LiteralText("IGT: ").append(new LiteralText(InGameTimer.timeToStringFormat(timer.getInGameTime())));
                MutableText rta = new LiteralText("RTA: ").append(new LiteralText(InGameTimer.timeToStringFormat(timer.getRealTimeAttack())));
                switch (pos) {
                    case LEFT_TOP:
                        y = 12;
                        break;
                    case RIGHT_BOTTOM:
                        x = window.getScaledWidth() - 12 - this.textRenderer.getWidth(rta);
                        y = window.getScaledHeight() - 32;
                        break;
                    case RIGHT_TOP:
                        x = window.getScaledWidth() - 12 - this.textRenderer.getWidth(rta);
                        y = 12;
                        break;
                }
                if ((!this.isPaused() || this.currentScreen instanceof GameMenuScreen) && !(this.currentScreen instanceof ChatScreen)) {
                    drawOutLine(this.textRenderer, matrixStack, x, y+10, rta, Formatting.AQUA.getColorValue());
                    drawOutLine(this.textRenderer, matrixStack, x, y, igt, Formatting.YELLOW.getColorValue());
                }
            }
        }
    }

    private static void drawOutLine(TextRenderer textRenderer, MatrixStack matrixStack, int x, int y, MutableText text, Integer color) {
        textRenderer.draw(matrixStack, text, (float)x + 1, (float)y + 1, 0);
        textRenderer.draw(matrixStack, text, (float)x + 1, (float)y, 0);
        textRenderer.draw(matrixStack, text, (float)x + 1, (float)y - 1, 0);
        textRenderer.draw(matrixStack, text, (float)x, (float)y - 1, 0);
        textRenderer.draw(matrixStack, text, (float)x, (float)y + 1, 0);
        textRenderer.draw(matrixStack, text, (float)x - 1, (float)y + 1, 0);
        textRenderer.draw(matrixStack, text, (float)x - 1, (float)y, 0);
        textRenderer.draw(matrixStack, text, (float)x - 1, (float)y - 1, 0);
        textRenderer.draw(matrixStack, text, (float)x, (float)y, color);
    }
}

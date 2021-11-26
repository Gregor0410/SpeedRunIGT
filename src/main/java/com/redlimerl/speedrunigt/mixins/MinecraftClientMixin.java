package com.redlimerl.speedrunigt.mixins;

import com.redlimerl.speedrunigt.SpeedRunIGT;
import com.redlimerl.speedrunigt.timer.InGameTimer;
import com.redlimerl.speedrunigt.timer.RunCategory;
import com.redlimerl.speedrunigt.timer.TimerStatus;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.registry.DynamicRegistryManager;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.level.LevelInfo;
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

    @Shadow public abstract boolean isPaused();

    @Shadow @Nullable public Screen currentScreen;

    @Shadow @Final public Mouse mouse;

    @Shadow @Final public WorldRenderer worldRenderer;

    @Shadow @Nullable public ClientWorld world;

    @Inject(at = @At("HEAD"), method = "createWorld")
    public void onCreate(String worldName, LevelInfo levelInfo, DynamicRegistryManager.Impl registryTracker, GeneratorOptions generatorOptions, CallbackInfo ci) {
        InGameTimer.start();
        InGameTimer.currentWorldName = worldName;
    }

    @Inject(at = @At("HEAD"), method = "startIntegratedServer(Ljava/lang/String;)V")
    public void onWorldOpen(String worldName, CallbackInfo ci) {
        InGameTimer timer = InGameTimer.getInstance();
        boolean loaded = InGameTimer.load(worldName);
        if (!loaded) timer.end();
        else {
            InGameTimer.currentWorldName = worldName;
            timer.setPause(true, TimerStatus.IDLE);
        }
    }

    private static ClientWorld currWorld;
    @Inject(at = @At("HEAD"), method = "joinWorld")
    public void onJoin(ClientWorld targetWorld, CallbackInfo ci) {
        if (!isInSingleplayer()) return;
        InGameTimer timer = InGameTimer.getInstance();

        currWorld = targetWorld;

        if (timer.getStatus() != TimerStatus.NONE) {
            timer.setPause(true, TimerStatus.IDLE);
            InGameTimer.renderedWorld = 0;
        }

        //Enter Nether
        if (timer.getCategory() == RunCategory.ENTER_NETHER && Objects.equals(targetWorld.getRegistryKey().getValue().toString(), DimensionType.THE_NETHER_REGISTRY_KEY.toString())) {
            timer.complete();
        }

        //Enter End
        if (timer.getCategory() == RunCategory.ENTER_END && Objects.equals(targetWorld.getRegistryKey().getValue().toString(), DimensionType.THE_END_REGISTRY_KEY.toString())) {
            timer.complete();
        }
    }

    @ModifyVariable(method = "render(Z)V", at = @At(value = "STORE"), ordinal = 1)
    private boolean renderMixin(boolean paused) {
        InGameTimer timer = InGameTimer.getInstance();

        if (timer.getStatus() == TimerStatus.RUNNING && paused) {
            timer.setPause(true, TimerStatus.PAUSED);
        } else if (timer.getStatus() == TimerStatus.PAUSED && !paused) {
            timer.setPause(false);
        }
        if (!paused && mouse.isCursorLocked()) {
            timer.startRSGTime();
        }
        if (paused && !timer.isStarted()) {
            timer.resetRSGTime();
        }

        return paused;
    }

    @Inject(method = "handleInputEvents", at = @At(value = "HEAD"))
    private void slotChange(CallbackInfo ci) {
        GameOptions o = this.options;
        InGameTimer timer = InGameTimer.getInstance();

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
        InGameTimer timer = InGameTimer.getInstance();

        int chunks = worldRenderer.getCompletedChunkCount();
        int entities = worldRenderer.regularEntityCount - (options.getPerspective().isFirstPerson() ? 0 : 1);
        SpeedRunIGT.DEBUG_DATA = timer.getStatus().name() + chunks + ", " + entities;

        if (world == currWorld && timer.getStatus() == TimerStatus.IDLE && timer.isStarted() && isPaused() && chunks + entities > 0) {
           timer.setPause(false);
        }
        if (!this.options.hudHidden && this.isInSingleplayer() && this.world != null && timer.getStatus() != TimerStatus.NONE
                && (this.isPaused() || this.currentScreen instanceof GameMenuScreen) && !(this.currentScreen instanceof ChatScreen)) {
            SpeedRunIGT.TIMER_DRAWER.draw();
        }
    }
}

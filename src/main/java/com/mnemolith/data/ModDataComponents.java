package com.mnemolith.data;

import com.mnemolith.Mnemolith;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Item data components. Imprint slips carry {@link ImprintCast}. */
public final class ModDataComponents {
    public static final DeferredRegister.DataComponents DATA_COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Mnemolith.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ImprintCast>> IMPRINT_CAST = DATA_COMPONENTS.registerComponentType(
            "imprint_cast",
            builder -> builder.persistent(ImprintCast.CODEC).networkSynchronized(ImprintCast.STREAM_CODEC));

    /** A filled echo recording: frames and world actions of one self-recording. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<com.mnemolith.echo.EchoRecording>> ECHO_RECORDING = DATA_COMPONENTS.registerComponentType(
            "echo_recording",
            builder -> builder.persistent(com.mnemolith.echo.EchoRecording.CODEC).networkSynchronized(com.mnemolith.echo.EchoRecording.NETWORK_CODEC));

    /** What a filled recording teaches (mining targets, blueprint). Small; this is what the client reads. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<com.mnemolith.echo.EchoLesson>> ECHO_LESSON = DATA_COMPONENTS.registerComponentType(
            "echo_lesson",
            builder -> builder.persistent(com.mnemolith.echo.EchoLesson.CODEC).networkSynchronized(com.mnemolith.echo.EchoLesson.STREAM_CODEC));

    private ModDataComponents() {}

    public static void register(IEventBus modEventBus) {
        DATA_COMPONENTS.register(modEventBus);
    }
}

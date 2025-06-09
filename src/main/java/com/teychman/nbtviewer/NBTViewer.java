package com.teychman.nbtviewer;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(NBTViewer.MODID)
public class NBTViewer {
    public static final String MODID = "nbtviewer";

    public NBTViewer() {
        // Регистрация клиентских событий
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onClientSetup);
    }

    private void onClientSetup(final FMLClientSetupEvent event) {
        // Ничего дополнительно не нужно
    }
}

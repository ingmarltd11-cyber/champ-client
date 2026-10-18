package net.champ.bootstrap;

import imgui.ChampAndroidNative;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

public final class ChampNativeBootstrap implements PreLaunchEntrypoint {
   public void onPreLaunch() {
      ChampAndroidNative.prepare();
   }
}

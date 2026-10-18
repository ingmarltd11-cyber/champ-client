package imgui.glfw;

import imgui.ChampAndroidNative;
import imgui.ImGui;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWCharModsCallback;

@Environment(EnvType.CLIENT)
public class ChampGlfw extends ImGuiImplGlfw {
   private final boolean android;
   private GLFWCharModsCallback ownedCharMods;
   private GLFWCharModsCallback previousCharMods;
   private long charModsWindow;
   private boolean usingCharMods;
   private boolean reportedCharacters;
   private boolean wantedTextInput;

   public ChampGlfw() {
      this(ChampAndroidNative.isAndroidRuntime());
   }

   protected ChampGlfw(boolean android) {
      this.android = android;
   }

   public boolean init(long window, boolean installCallbacks) {
      boolean initialized = super.init(window, installCallbacks);
      if (initialized && this.android) {
         ImGui.getIO().removeBackendFlags(2);
         System.err.println("[Champ ImGui] Android cursor management delegated to launcher");
      }

      return initialized;
   }

   protected void updateMouseCursor() {
      if (!this.android) {
         super.updateMouseCursor();
      }
   }

   public void installCallbacks(long window) {
      super.installCallbacks(window);
      if (this.android) {
         this.ownedCharMods = GLFWCharModsCallback.create(this::charModsCallback);

         try {
            this.previousCharMods = GLFW.glfwSetCharModsCallback(window, this.ownedCharMods);
            this.charModsWindow = window;
            this.usingCharMods = true;
            System.err.println("[Champ ImGui] Android text input: Minecraft character-with-modifiers channel enabled");
         } catch (LinkageError failure) {
            this.ownedCharMods.free();
            this.ownedCharMods = null;
            System.err
               .println("[Champ ImGui] Character-with-modifiers callback unavailable; keeping regular character input: " + failure.getClass().getSimpleName());
         }
      }
   }

   public void charModsCallback(long window, int codepoint, int modifiers) {
      if (this.previousCharMods != null && this.shouldChainCallback(window)) {
         this.previousCharMods.invoke(window, codepoint, modifiers);
      }

      if (this.usingCharMods && window == this.charModsWindow) {
         ImGui.getIO().addInputCharacter(codepoint);
         if (!this.reportedCharacters) {
            System.err.println("[Champ ImGui] Android text character received");
            this.reportedCharacters = true;
         }
      }
   }

   public void charCallback(long window, int codepoint) {
      if (!this.usingCharMods || window != this.charModsWindow) {
         super.charCallback(window, codepoint);
      } else if (this.data.prevUserCallbackChar != null && this.shouldChainCallback(window)) {
         this.data.prevUserCallbackChar.invoke(window, codepoint);
      }
   }

   public void newFrame() {
      super.newFrame();
      if (this.usingCharMods) {
         boolean wants = ImGui.getIO().getWantTextInput();
         if (wants != this.wantedTextInput) {
            System.err.println("[Champ ImGui] Text field " + (wants ? "focused" : "unfocused"));
            this.wantedTextInput = wants;
         }
      }
   }

   public void windowFocusCallback(long window, boolean focused) {
      if (!focused) {
         ChampKeys.clear(window);
      }

      super.windowFocusCallback(window, focused);
   }

   public void restoreCallbacks(long window) {
      ChampKeys.clear(window);
      if (this.usingCharMods && window == this.charModsWindow) {
         GLFW.glfwSetCharModsCallback(window, this.previousCharMods);
         this.usingCharMods = false;
         this.previousCharMods = null;
         this.charModsWindow = 0L;
         this.ownedCharMods.free();
         this.ownedCharMods = null;
         this.reportedCharacters = false;
         this.wantedTextInput = false;
      }

      super.restoreCallbacks(window);
   }
}

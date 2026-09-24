package dihclient.util;

import java.util.List;

public class MeteorMacroAdapter {
    public static List<DihMacro> getMeteorMacros() {
        return DihCompatManager.getMeteorMacros();
    }

    public static void importToMeteor(DihMacro packUtilMacro) {
        if (DihCompatManager.importToMeteor(packUtilMacro)) {
            DihClientMessaging.sendPrefixed("§aImported to Meteor: " + packUtilMacro.name);
        } else {
            DihClientMessaging.sendPrefixed("§cFailed to import to Meteor");
        }
    }

    public static boolean importToDih(String macroName) {
        DihMacro packUtilMacro = DihCompatManager.getMeteorMacro(macroName);
        if (packUtilMacro == null) {
            DihClientMessaging.sendPrefixed("§cMeteor macro not found: " + macroName);
            return false;
        }

        DihMacro imported = DihMacroManager.get().addImportedCopy(packUtilMacro, packUtilMacro.name);
        if (imported == null) return false;

        if (!imported.name.equals(packUtilMacro.name)) {
            DihClientMessaging.sendPrefixed("§eImported Meteor macro as: " + imported.name);
        }
        return true;
    }
}

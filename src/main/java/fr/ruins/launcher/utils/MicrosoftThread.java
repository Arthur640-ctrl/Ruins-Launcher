package fr.ruins.launcher.utils;

import fr.litarvan.openauth.microsoft.MicrosoftAuthenticationException;
import fr.ruins.launcher.Launcher;

public class MicrosoftThread implements Runnable {
    @Override
    public void run() {
        try {
            Launcher.auth();
        } catch (MicrosoftAuthenticationException e) {
            Launcher.get_reporter().catchError(e, "Impossible de se connecter");
        }
    }
}

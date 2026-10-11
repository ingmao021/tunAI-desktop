package com.tunai.desktop.ui;

import com.tunai.desktop.api.Quota;

/** Textos del contador de cuota (§5.2, §5.3). */
public final class QuotaText {

    private QuotaText() {
    }

    public static String header(Quota quota) {
        if (quota == null) {
            return "Modelos 3D hoy: –";
        }
        if (quota.exhausted()) {
            return "Taller cerrado por hoy · reabre a las 00:00";
        }
        return "Modelos 3D hoy: " + quota.used() + " de " + quota.limit();
    }

    public static String generationWarning(Quota quota) {
        if (quota == null) {
            return "⚠ Esto usará 1 de tus generaciones de hoy.";
        }
        int left = Math.max(0, quota.remaining() - 1);
        return "⚠ Esto usará 1 de tus " + quota.limit() + " generaciones de hoy. Después te quedará"
                + (left == 1 ? " 1." : "n " + left + ".");
    }
}

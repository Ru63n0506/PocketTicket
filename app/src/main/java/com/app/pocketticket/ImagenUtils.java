package com.app.pocketticket;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.InputStream;

/**
 * Archivo: app/src/main/java/com/app/pocketticket/ImagenUtils.java
 *
 * Utilidad para cargar las fotos de los tickets SIN Glide y SIN provocar
 * OutOfMemoryError: primero se leen solo las dimensiones (inJustDecodeBounds)
 * y después se decodifica con inSampleSize.
 */
public final class ImagenUtils {

    private ImagenUtils() {
        // Clase de utilidades: no se instancia.
    }

    /**
     * Decodifica un bitmap reducido a partir de una Uri content:// o file://.
     *
     * @param ladoDeseadoPx tamaño aproximado (en px) del lado mayor de la miniatura.
     * @return el Bitmap, o null si no se pudo leer.
     */
    @Nullable
    public static Bitmap cargarBitmap(@NonNull Context contexto,
                                      @Nullable Uri uri,
                                      int ladoDeseadoPx) {

        if (uri == null || ladoDeseadoPx <= 0) {
            return null;
        }

        // 1) Leer únicamente las dimensiones del archivo.
        BitmapFactory.Options limites = new BitmapFactory.Options();
        limites.inJustDecodeBounds = true;

        try (InputStream entrada = contexto.getContentResolver().openInputStream(uri)) {
            if (entrada == null) {
                return null;
            }
            BitmapFactory.decodeStream(entrada, null, limites);
        } catch (Exception e) {
            return null;
        }

        if (limites.outWidth <= 0 || limites.outHeight <= 0) {
            return null;
        }

        // 2) Decodificar de verdad, reduciendo la resolución.
        BitmapFactory.Options opciones = new BitmapFactory.Options();
        opciones.inSampleSize = calcularInSampleSize(
                limites.outWidth, limites.outHeight, ladoDeseadoPx);
        // RGB_565 usa la mitad de memoria que ARGB_8888 (suficiente para fotos).
        opciones.inPreferredConfig = Bitmap.Config.RGB_565;

        try (InputStream entrada = contexto.getContentResolver().openInputStream(uri)) {
            if (entrada == null) {
                return null;
            }
            return BitmapFactory.decodeStream(entrada, null, opciones);
        } catch (Exception e) {
            return null;
        }
    }

    /** Calcula el factor de reducción (potencia de 2) para no cargar la foto completa. */
    private static int calcularInSampleSize(int ancho, int alto, int ladoDeseadoPx) {
        int muestra = 1;
        int ladoMenor = Math.min(ancho, alto);
        while (ladoMenor / (muestra * 2) >= ladoDeseadoPx) {
            muestra *= 2;
        }
        return muestra;
    }
}

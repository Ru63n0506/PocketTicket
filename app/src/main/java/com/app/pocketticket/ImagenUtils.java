package com.app.pocketticket;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.InputStream;

public final class ImagenUtils {

    private ImagenUtils() {
    }

    @Nullable
    public static Bitmap cargarBitmap(@NonNull Context contexto,
                                      @Nullable Uri uri,
                                      int ladoDeseadoPx) {

        if (uri == null || ladoDeseadoPx <= 0) {
            return null;
        }

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

        BitmapFactory.Options opciones = new BitmapFactory.Options();
        opciones.inSampleSize = calcularInSampleSize(
                limites.outWidth, limites.outHeight, ladoDeseadoPx);
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

    private static int calcularInSampleSize(int ancho, int alto, int ladoDeseadoPx) {
        int muestra = 1;
        int ladoMenor = Math.min(ancho, alto);
        while (ladoMenor / (muestra * 2) >= ladoDeseadoPx) {
            muestra *= 2;
        }
        return muestra;
    }
}
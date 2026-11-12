package com.app.pocketticket;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Archivo: app/src/main/java/com/app/pocketticket/GastoAdapter.java
 *
 * Adaptador estándar RecyclerView.Adapter para la lista de gastos.
 * Las miniaturas se cargan en segundo plano con BitmapFactory + inSampleSize
 * (sin dependencias externas tipo Glide) y se evita el problema clásico de
 * "imagen incorrecta al reciclar filas" comprobando el tag de la ImageView.
 */
public class GastoAdapter extends RecyclerView.Adapter<GastoAdapter.GastoViewHolder> {

    private static final int LADO_MINIATURA_PX = 250;

    /** Hilo de trabajo compartido por todas las filas. */
    private static final ExecutorService EJECUTOR = Executors.newFixedThreadPool(2);
    /** Handler para regresar al hilo principal. */
    private static final Handler HILO_UI = new Handler(Looper.getMainLooper());

    private final List<Gasto> gastos = new ArrayList<>();
    private final Context contexto;
    private final NumberFormat formatoMoneda;
    private final int paddingPlaceholderPx;

    public GastoAdapter(@NonNull Context context) {
        this.contexto = context.getApplicationContext();
        this.formatoMoneda = NumberFormat.getCurrencyInstance(new Locale("es", "MX"));
        this.paddingPlaceholderPx = Math.round(
                16 * contexto.getResources().getDisplayMetrics().density);
    }

    /** Reemplaza el contenido de la lista (nunca recibe null). */
    public void actualizarLista(@Nullable List<Gasto> nuevaLista) {
        gastos.clear();
        if (nuevaLista != null) {
            gastos.addAll(nuevaLista);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public GastoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View fila = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_gasto, parent, false);
        return new GastoViewHolder(fila);
    }

    @Override
    public void onBindViewHolder(@NonNull GastoViewHolder holder, int position) {
        Gasto gasto = gastos.get(position);

        holder.tvConcepto.setText(gasto.getConcepto());

        String fecha = gasto.getFecha();
        holder.tvFecha.setText((fecha == null || fecha.trim().isEmpty())
                ? contexto.getString(R.string.sin_fecha)
                : fecha);

        holder.tvMonto.setText("- " + formatoMoneda.format(gasto.getMonto()));

        if (gasto.tieneImagen()) {
            mostrarPlaceholder(holder.ivMiniatura);
            cargarMiniatura(holder.ivMiniatura, gasto.getImagenUri());
        } else {
            holder.ivMiniatura.setTag(null);
            holder.ivMiniatura.setPadding(paddingPlaceholderPx, paddingPlaceholderPx,
                    paddingPlaceholderPx, paddingPlaceholderPx);
            holder.ivMiniatura.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            holder.ivMiniatura.setImageResource(R.drawable.ic_receipt);
        }
    }

    @Override
    public int getItemCount() {
        return gastos.size();
    }

    /* ------------------------- Carga de miniaturas ------------------------- */

    private void mostrarPlaceholder(@NonNull ImageView destino) {
        destino.setPadding(0, 0, 0, 0);
        destino.setScaleType(ImageView.ScaleType.CENTER_CROP);
        destino.setImageResource(R.drawable.ic_receipt);
    }

    private void cargarMiniatura(@NonNull final ImageView destino,
                                 @NonNull final String uriTexto) {

        // El tag permite descartar resultados de filas ya recicladas.
        destino.setTag(uriTexto);

        final Uri uri;
        try {
            uri = Uri.parse(uriTexto);
        } catch (Exception e) {
            return;
        }

        EJECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                final Bitmap bitmap = ImagenUtils.cargarBitmap(contexto, uri, LADO_MINIATURA_PX);
                if (bitmap == null) {
                    return;
                }
                HILO_UI.post(new Runnable() {
                    @Override
                    public void run() {
                        if (uriTexto.equals(destino.getTag())) {
                            destino.setImageBitmap(bitmap);
                        }
                    }
                });
            }
        });
    }

    /* ----------------------------- ViewHolder ----------------------------- */

    static class GastoViewHolder extends RecyclerView.ViewHolder {

        final TextView tvConcepto;
        final TextView tvFecha;
        final TextView tvMonto;
        final ImageView ivMiniatura;

        GastoViewHolder(@NonNull View itemView) {
            super(itemView);
            tvConcepto = itemView.findViewById(R.id.tvConcepto);
            tvFecha = itemView.findViewById(R.id.tvFecha);
            tvMonto = itemView.findViewById(R.id.tvMonto);
            ivMiniatura = itemView.findViewById(R.id.ivMiniatura);
        }
    }
}

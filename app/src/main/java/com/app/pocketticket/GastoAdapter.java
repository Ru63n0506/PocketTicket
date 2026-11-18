package com.app.pocketticket;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
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

public class GastoAdapter extends RecyclerView.Adapter<GastoAdapter.GastoViewHolder> {

    public interface OnGastoListener {
        void onEliminar(@NonNull Gasto gasto);

        void onVerFoto(@NonNull Gasto gasto);
    }

    private static final int LADO_MINIATURA_PX = 250;
    private static final ExecutorService EJECUTOR = Executors.newFixedThreadPool(2);
    private static final Handler HILO_UI = new Handler(Looper.getMainLooper());

    private final List<Gasto> gastos = new ArrayList<>();
    private final Context contexto;
    private final NumberFormat formatoMoneda;
    private final OnGastoListener listener;
    private final int paddingMiniaturaPx;

    public GastoAdapter(@NonNull Context context, @NonNull OnGastoListener listener) {
        this.contexto = context.getApplicationContext();
        this.listener = listener;
        this.formatoMoneda = NumberFormat.getCurrencyInstance(new Locale("es", "MX"));
        this.paddingMiniaturaPx = Math.round(
                8 * contexto.getResources().getDisplayMetrics().density);
    }

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

        holder.btnEliminar.setOnClickListener(v -> listener.onEliminar(gasto));

        holder.itemView.setOnClickListener(v -> {
            if (gasto.tieneImagen()) {
                listener.onVerFoto(gasto);
            }
        });

        holder.ivMiniatura.setOnClickListener(v -> {
            if (gasto.tieneImagen()) {
                listener.onVerFoto(gasto);
            }
        });

        if (gasto.tieneImagen()) {
            holder.ivMiniatura.setTag(gasto.getImagenUri());
            holder.ivMiniatura.setPadding(0, 0, 0, 0);
            holder.ivMiniatura.setScaleType(ImageView.ScaleType.CENTER_CROP);
            holder.ivMiniatura.setImageResource(R.drawable.ic_receipt);
            cargarMiniatura(holder.ivMiniatura, gasto.getImagenUri());
        } else {
            holder.ivMiniatura.setTag(null);
            holder.ivMiniatura.setPadding(paddingMiniaturaPx, paddingMiniaturaPx,
                    paddingMiniaturaPx, paddingMiniaturaPx);
            holder.ivMiniatura.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            holder.ivMiniatura.setImageResource(R.drawable.ic_receipt);
        }
    }

    @Override
    public int getItemCount() {
        return gastos.size();
    }

    private void cargarMiniatura(@NonNull final ImageView destino,
                                 @NonNull final String uriTexto) {

        final Uri uri;
        try {
            uri = Uri.parse(uriTexto);
        } catch (Exception e) {
            return;
        }

        EJECUTOR.execute(() -> {
            final Bitmap bitmap = ImagenUtils.cargarBitmap(contexto, uri, LADO_MINIATURA_PX);
            if (bitmap == null) {
                return;
            }
            HILO_UI.post(() -> {
                if (uriTexto.equals(destino.getTag())) {
                    destino.setImageBitmap(bitmap);
                }
            });
        });
    }

    static class GastoViewHolder extends RecyclerView.ViewHolder {

        final TextView tvConcepto;
        final TextView tvFecha;
        final TextView tvMonto;
        final ImageView ivMiniatura;
        final Button btnEliminar;

        GastoViewHolder(@NonNull View itemView) {
            super(itemView);
            tvConcepto = itemView.findViewById(R.id.tvConcepto);
            tvFecha = itemView.findViewById(R.id.tvFecha);
            tvMonto = itemView.findViewById(R.id.tvMonto);
            ivMiniatura = itemView.findViewById(R.id.ivMiniatura);
            btnEliminar = itemView.findViewById(R.id.btnEliminar);
        }
    }
}
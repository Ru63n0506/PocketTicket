package com.app.pocketticket;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.IOException;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements GastoAdapter.OnGastoListener {

    private static final double PRESUPUESTO_INICIAL = 5000.0d;
    private static final int LIMITE_GASTOS_VISIBLES = 100;
    private static final String SUFIJO_PROVEEDOR = ".fileprovider";
    private static final String CARPETA_FOTOS = "tickets";
    private static final String KEY_FOTO_URI = "key_foto_uri";
    private static final String KEY_FOTO_RUTA = "key_foto_ruta";

    private TextView tvSaldo;
    private TextView tvResumen;
    private TextView tvSinGastos;
    private RecyclerView rvGastos;

    private DatabaseHelper db;
    private GastoAdapter adaptador;

    private final NumberFormat formatoMoneda =
            NumberFormat.getCurrencyInstance(new Locale("es", "MX"));

    private ActivityResultLauncher<Uri> lanzadorCamara;
    private ActivityResultLauncher<String> lanzadorPermisoCamara;

    @Nullable
    private Uri uriFotoPendiente;

    @Nullable
    private File archivoFotoPendiente;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        db = DatabaseHelper.getInstancia(this);

        enlazarVistas();
        configurarListaDeGastos();
        restaurarFotoPendiente(savedInstanceState);
        registrarLanzadores();
        configurarBotones();

        cargarDatos();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (uriFotoPendiente != null) {
            outState.putString(KEY_FOTO_URI, uriFotoPendiente.toString());
        }
        if (archivoFotoPendiente != null) {
            outState.putString(KEY_FOTO_RUTA, archivoFotoPendiente.getAbsolutePath());
        }
    }

    private void enlazarVistas() {
        tvSaldo = findViewById(R.id.tvSaldo);
        tvResumen = findViewById(R.id.tvResumen);
        tvSinGastos = findViewById(R.id.tvSinGastos);
        rvGastos = findViewById(R.id.rvGastos);
    }

    private void configurarListaDeGastos() {
        adaptador = new GastoAdapter(this, this);
        rvGastos.setLayoutManager(new LinearLayoutManager(this));
        rvGastos.setHasFixedSize(true);
        rvGastos.setAdapter(adaptador);
    }

    private void configurarBotones() {
        Button btnTomarFoto = findViewById(R.id.btnTomarFoto);
        Button btnGastoManual = findViewById(R.id.btnGastoManual);
        Button btnAgregarSaldo = findViewById(R.id.btnAgregarSaldo);

        btnTomarFoto.setOnClickListener(v -> solicitarCamara());
        btnGastoManual.setOnClickListener(v -> mostrarDialogoGasto(null));
        btnAgregarSaldo.setOnClickListener(v -> mostrarDialogoSaldo());
    }

    private void registrarLanzadores() {
        lanzadorCamara = registerForActivityResult(
                new ActivityResultContracts.TakePicture(),
                guardada -> {
                    if (fotoValida()) {
                        mostrarDialogoGasto(uriFotoPendiente.toString());
                    } else {
                        borrarFotoPendiente();
                        mostrarToast(R.string.foto_no_guardada);
                    }
                });

        lanzadorPermisoCamara = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                concedido -> {
                    if (concedido) {
                        abrirCamara();
                    } else {
                        mostrarDialogoPermisoDenegado();
                    }
                });
    }

    private void cargarDatos() {
        ArrayList<Gasto> gastos = db.obtenerUltimosGastos(LIMITE_GASTOS_VISIBLES);
        adaptador.actualizarLista(gastos);

        double totalGastado = db.obtenerTotalGastos();
        double totalAgregado = db.obtenerTotalIngresos();
        double saldo = PRESUPUESTO_INICIAL + totalAgregado - totalGastado;

        tvSaldo.setText(formatoMoneda.format(saldo));
        tvSaldo.setTextColor(ContextCompat.getColor(this,
                saldo < 0 ? R.color.rojo_claro : R.color.blanco));

        int cantidad = db.contarGastos();
        tvResumen.setText(getString(R.string.saldo_resumen, cantidad,
                formatoMoneda.format(totalGastado),
                formatoMoneda.format(totalAgregado)));

        boolean sinGastos = gastos.isEmpty();
        tvSinGastos.setVisibility(sinGastos ? View.VISIBLE : View.GONE);
        rvGastos.setVisibility(sinGastos ? View.GONE : View.VISIBLE);
    }

    private void solicitarCamara() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            abrirCamara();
        } else {
            lanzadorPermisoCamara.launch(Manifest.permission.CAMERA);
        }
    }

    private void abrirCamara() {
        final File archivo;
        final Uri uri;

        try {
            archivo = crearArchivoDeFoto();
            if (archivo == null) {
                mostrarToast(R.string.error_crear_foto);
                return;
            }

            uri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + SUFIJO_PROVEEDOR,
                    archivo);
        } catch (IOException | IllegalArgumentException e) {
            borrarFotoPendiente();
            mostrarToast(R.string.error_crear_foto);
            return;
        }

        archivoFotoPendiente = archivo;
        uriFotoPendiente = uri;

        try {
            lanzadorCamara.launch(uri);
        } catch (ActivityNotFoundException e) {
            borrarFotoPendiente();
            mostrarToast(R.string.sin_camara);
        }
    }

    @Nullable
    private File crearArchivoDeFoto() throws IOException {
        String marcaTiempo = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                .format(new Date());
        String nombreArchivo = "TICKET_" + marcaTiempo + ".jpg";

        File carpeta = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (carpeta != null) {
            carpeta = new File(carpeta, CARPETA_FOTOS);
        } else {
            carpeta = new File(getFilesDir(), CARPETA_FOTOS);
        }

        if (!carpeta.exists() && !carpeta.mkdirs()) {
            return null;
        }
        return new File(carpeta, nombreArchivo);
    }

    private boolean fotoValida() {
        return archivoFotoPendiente != null
                && archivoFotoPendiente.exists()
                && archivoFotoPendiente.length() > 0
                && uriFotoPendiente != null;
    }

    private void borrarFotoPendiente() {
        if (archivoFotoPendiente != null && archivoFotoPendiente.exists()) {
            archivoFotoPendiente.delete();
        }
        archivoFotoPendiente = null;
        uriFotoPendiente = null;
    }

    private void mostrarDialogoPermisoDenegado() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.permiso_camara_titulo)
                .setMessage(R.string.permiso_camara_mensaje)
                .setPositiveButton(R.string.abrir_ajustes, (d, w) -> abrirAjustesDeLaApp())
                .setNegativeButton(R.string.cerrar, null)
                .show();
    }

    private void abrirAjustesDeLaApp() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", getPackageName(), null));
        startActivity(intent);
    }

    private void mostrarDialogoSaldo() {
        final View vista = getLayoutInflater()
                .inflate(R.layout.dialog_agregar_saldo, null);

        final EditText etMontoSaldo = vista.findViewById(R.id.etMontoSaldo);
        final Button btnCancelar = vista.findViewById(R.id.btnCancelarSaldo);
        final Button btnAgregar = vista.findViewById(R.id.btnAgregarSaldo);

        final AlertDialog dialogo = new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_titulo_saldo)
                .setView(vista)
                .setCancelable(true)
                .create();

        btnCancelar.setOnClickListener(v -> dialogo.dismiss());

        btnAgregar.setOnClickListener(v -> {
            etMontoSaldo.setError(null);

            double monto = parsearMonto(textoDe(etMontoSaldo));
            if (!esMontoValido(monto)) {
                etMontoSaldo.setError(getString(R.string.error_monto));
                etMontoSaldo.requestFocus();
                return;
            }

            long id = db.insertarIngreso(monto, fechaActual());
            if (id == -1) {
                mostrarToast(R.string.error_saldo);
                return;
            }

            dialogo.dismiss();
            cargarDatos();
            mostrarToast(R.string.saldo_agregado);
        });

        dialogo.show();

        if (dialogo.getWindow() != null) {
            dialogo.getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        etMontoSaldo.requestFocus();
    }

    private void mostrarDialogoGasto(@Nullable final String imagenUriTexto) {

        final View vista = getLayoutInflater()
                .inflate(R.layout.dialog_agregar_gasto, null);

        final EditText etConcepto = vista.findViewById(R.id.etConcepto);
        final EditText etMonto = vista.findViewById(R.id.etMonto);
        final Button btnGuardar = vista.findViewById(R.id.btnGuardar);
        final Button btnCancelar = vista.findViewById(R.id.btnCancelar);
        final ImageView imgPreview = vista.findViewById(R.id.imgPreviewTicket);

        if (imagenUriTexto != null) {
            mostrarPreview(imgPreview, imagenUriTexto);
        }

        final boolean[] guardado = new boolean[]{false};

        final AlertDialog dialogo = new AlertDialog.Builder(this)
                .setTitle(imagenUriTexto == null
                        ? R.string.dialog_titulo_manual
                        : R.string.dialog_titulo_ticket)
                .setView(vista)
                .setCancelable(true)
                .create();

        dialogo.setOnDismissListener(d -> {
            if (!guardado[0]) {
                borrarFotoPendiente();
            }
        });

        btnCancelar.setOnClickListener(v -> dialogo.dismiss());

        btnGuardar.setOnClickListener(v -> {
            etConcepto.setError(null);
            etMonto.setError(null);

            String concepto = textoDe(etConcepto);
            if (concepto.isEmpty()) {
                etConcepto.setError(getString(R.string.error_concepto));
                etConcepto.requestFocus();
                return;
            }

            double monto = parsearMonto(textoDe(etMonto));
            if (!esMontoValido(monto)) {
                etMonto.setError(getString(R.string.error_monto));
                etMonto.requestFocus();
                return;
            }

            long id = db.insertarGasto(concepto, monto, fechaActual(), imagenUriTexto);
            if (id == -1) {
                mostrarToast(R.string.error_guardar);
                return;
            }

            guardado[0] = true;
            uriFotoPendiente = null;
            archivoFotoPendiente = null;

            dialogo.dismiss();
            cargarDatos();
            mostrarToast(R.string.gasto_guardado);
        });

        dialogo.show();

        if (dialogo.getWindow() != null) {
            dialogo.getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        etConcepto.requestFocus();
    }

    private void mostrarPreview(@NonNull ImageView destino, @NonNull String uriTexto) {
        try {
            Bitmap miniatura = ImagenUtils.cargarBitmap(this, Uri.parse(uriTexto), 800);
            if (miniatura != null) {
                destino.setImageBitmap(miniatura);
                destino.setVisibility(View.VISIBLE);
            }
        } catch (Exception e) {
            destino.setVisibility(View.GONE);
        }
    }

    @Override
    public void onEliminar(@NonNull Gasto gasto) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialogo_eliminar_titulo)
                .setMessage(getString(R.string.dialogo_eliminar_mensaje, gasto.getConcepto()))
                .setPositiveButton(R.string.eliminar, (d, w) -> eliminarGasto(gasto))
                .setNegativeButton(R.string.boton_cancelar, null)
                .show();
    }

    @Override
    public void onVerFoto(@NonNull Gasto gasto) {
        String imagenUri = gasto.getImagenUri();
        if (imagenUri == null || imagenUri.trim().isEmpty()) {
            mostrarToast(R.string.sin_foto);
            return;
        }

        Bitmap bitmap = ImagenUtils.cargarBitmap(this, Uri.parse(imagenUri), 1200);
        if (bitmap == null) {
            mostrarToast(R.string.sin_foto);
            return;
        }

        View vista = getLayoutInflater().inflate(R.layout.dialog_ver_foto, null);
        ImageView ivFoto = vista.findViewById(R.id.ivFotoGrande);
        Button btnCerrar = vista.findViewById(R.id.btnCerrarFoto);

        ivFoto.setImageBitmap(bitmap);

        final AlertDialog dialogo = new AlertDialog.Builder(this)
                .setTitle(R.string.ver_ticket_titulo)
                .setView(vista)
                .create();

        btnCerrar.setOnClickListener(v -> dialogo.dismiss());
        dialogo.show();
    }

    private void eliminarGasto(@NonNull Gasto gasto) {
        if (!db.eliminarGasto(gasto.getId())) {
            mostrarToast(R.string.error_eliminar);
            return;
        }

        borrarFotoDeGasto(gasto.getImagenUri());
        cargarDatos();
        mostrarToast(R.string.gasto_eliminado);
    }

    private void borrarFotoDeGasto(@Nullable String imagenUri) {
        if (imagenUri == null || imagenUri.trim().isEmpty()) {
            return;
        }

        String nombre;
        try {
            nombre = Uri.parse(imagenUri).getLastPathSegment();
        } catch (Exception e) {
            return;
        }

        if (nombre == null || nombre.trim().isEmpty()) {
            return;
        }

        File externa = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File[] carpetas = new File[]{
                externa == null ? null : new File(externa, CARPETA_FOTOS),
                new File(getFilesDir(), CARPETA_FOTOS),
                new File(getCacheDir(), CARPETA_FOTOS)
        };

        for (File carpeta : carpetas) {
            if (carpeta == null) {
                continue;
            }
            File archivo = new File(carpeta, nombre);
            if (archivo.exists()) {
                archivo.delete();
            }
        }
    }

    @NonNull
    private static String textoDe(@NonNull EditText campo) {
        return campo.getText() == null ? "" : campo.getText().toString().trim();
    }

    private static double parsearMonto(@Nullable String texto) {
        if (texto == null) {
            return -1d;
        }
        String limpio = texto.trim()
                .replace("$", "")
                .replace(" ", "")
                .replace(',', '.');

        if (limpio.isEmpty()) {
            return -1d;
        }
        try {
            return Double.parseDouble(limpio);
        } catch (NumberFormatException e) {
            return -1d;
        }
    }

    private static boolean esMontoValido(double monto) {
        return monto > 0d && !Double.isNaN(monto) && !Double.isInfinite(monto);
    }

    @NonNull
    private static String fechaActual() {
        return new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                .format(new Date());
    }

    private void mostrarToast(@StringRes int mensaje) {
        Toast.makeText(this, mensaje, Toast.LENGTH_SHORT).show();
    }

    private void restaurarFotoPendiente(@Nullable Bundle estado) {
        if (estado == null) {
            return;
        }
        String uri = estado.getString(KEY_FOTO_URI);
        String ruta = estado.getString(KEY_FOTO_RUTA);

        if (uri != null) {
            uriFotoPendiente = Uri.parse(uri);
        }
        if (ruta != null) {
            archivoFotoPendiente = new File(ruta);
        }
    }
}